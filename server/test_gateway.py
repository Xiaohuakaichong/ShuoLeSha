import base64
import json
import unittest
from unittest.mock import patch

import httpx
from fastapi import HTTPException
from starlette.requests import Request

import main


WAV = b"RIFF0000WAVEfmt " + bytes(40)
DONE = 'data: {"type":"transcript.text.done","text":"周五交付原稿。"}\n\n'


class Stream(httpx.AsyncByteStream):
    def __init__(self, data):
        self.data, self.closed = data, False

    async def __aiter__(self):
        for part in self.data:
            yield part

    async def aclose(self):
        self.closed = True


class GatewayTests(unittest.IsolatedAsyncioTestCase):
    async def setup_gateway(self, handler, settings=None, remote="127.0.0.1"):
        self.upstream = httpx.AsyncClient(transport=httpx.MockTransport(handler))
        self.app = main.create_app(settings or main.Settings(upstream_key="upstream-test-only"))
        self.app.state.client = self.upstream
        self.client = httpx.AsyncClient(transport=httpx.ASGITransport(app=self.app, client=(remote, 123)), base_url="http://gateway")
        self.addAsyncCleanup(self.upstream.aclose)
        self.addAsyncCleanup(self.client.aclose)

    async def test_nested_step_plan_payload_and_raw_text(self):
        def handler(request):
            payload = json.loads(request.content)
            self.assertEqual("wav", payload["audio"]["input"]["format"]["type"])
            self.assertEqual(WAV, base64.b64decode(payload["audio"]["data"]))
            self.assertEqual("Bearer upstream-test-only", request.headers["authorization"])
            return httpx.Response(200, text=DONE)
        await self.setup_gateway(handler)
        response = await self.client.post("/v1/audio/transcriptions", files={"file": ("private.wav", WAV)})
        self.assertEqual(200, response.status_code)
        self.assertEqual("周五交付原稿。", response.json()["text"])

    async def test_gateway_auth_never_forwards_gateway_token(self):
        def handler(request):
            self.assertEqual("Bearer upstream-test-only", request.headers["authorization"])
            return httpx.Response(200, json={"choices": []})
        await self.setup_gateway(handler, main.Settings(upstream_key="upstream-test-only", gateway_token="gateway-test-only"))
        body = {"model": "llm", "messages": []}
        wrong = await self.client.post("/v1/chat/completions", json=body)
        self.assertEqual(401, wrong.status_code)
        good = await self.client.post("/v1/chat/completions", json=body, headers={"Authorization": "Bearer gateway-test-only"})
        self.assertEqual(200, good.status_code)

    async def test_remote_access_without_token_is_denied(self):
        await self.setup_gateway(lambda r: httpx.Response(200, json={}), remote="192.168.1.8")
        self.assertEqual(403, (await self.client.get("/health")).status_code)

    async def test_missing_upstream_configuration(self):
        await self.setup_gateway(lambda r: self.fail("should not reach upstream"), main.Settings())
        response = await self.client.post("/v1/chat/completions", json={"model": "x", "messages": []})
        self.assertEqual(401, response.status_code)

    async def test_rejected_upstream_status_is_preserved_without_body(self):
        stream = Stream([b"credential-private-error"])
        await self.setup_gateway(lambda r: httpx.Response(429, stream=stream))
        response = await self.client.post("/v1/chat/completions", json={"model": "x", "messages": []})
        self.assertEqual(429, response.status_code)
        self.assertNotIn("private", response.text)
        self.assertTrue(stream.closed)

    async def test_sse_connection_is_closed_after_forwarding(self):
        stream = Stream([DONE.encode()])
        await self.setup_gateway(lambda r: httpx.Response(200, stream=stream))
        response = await self.client.post("/v1/audio/asr/sse", json=main.step_payload(WAV, "wav", "asr"))
        self.assertEqual(200, response.status_code)
        self.assertIn("transcript.text.done", response.text)
        self.assertTrue(stream.closed)

    async def test_disconnected_sse_iterator_releases_upstream(self):
        stream = Stream([b"data: first\n\n", b"data: second\n\n"])
        await self.setup_gateway(lambda r: httpx.Response(200, stream=stream))
        body = json.dumps(main.step_payload(WAV, "wav", "asr")).encode()
        async def receive():
            return {"type": "http.request", "body": body, "more_body": False}
        request = Request({"type": "http", "method": "POST", "path": "/v1/audio/asr/sse", "headers": [], "app": self.app}, receive)
        endpoint = next(route.endpoint for route in self.app.routes if getattr(route, "path", "") == "/v1/audio/asr/sse")
        response = await endpoint(request, None)
        await anext(response.body_iterator)
        await response.body_iterator.aclose()
        self.assertTrue(stream.closed)

    async def test_aac_is_never_mislabeled_as_ogg(self):
        await self.setup_gateway(lambda r: self.fail("unsupported upload reached upstream"))
        response = await self.client.post("/v1/audio/transcriptions", files={"file": ("audio.aac", b"\xff\xf1\x60\x40\x01\x5f\xfc\x00")})
        self.assertEqual(415, response.status_code)

    async def test_malformed_step_payload_is_rejected(self):
        await self.setup_gateway(lambda r: self.fail("invalid payload reached upstream"))
        response = await self.client.post("/v1/audio/asr/sse", json={"audio": "legacy base64", "format": {"type": "ogg"}})
        self.assertEqual(400, response.status_code)

    async def test_invalid_json_and_empty_audio(self):
        await self.setup_gateway(lambda r: self.fail("invalid body reached upstream"))
        self.assertEqual(400, (await self.client.post("/v1/chat/completions", content="not-json")).status_code)
        self.assertEqual(400, (await self.client.post("/v1/audio/transcriptions", files={"file": ("empty.wav", b"")})).status_code)

    async def test_audio_and_json_limits(self):
        await self.setup_gateway(lambda r: self.fail("large request reached upstream"))
        with patch.object(main, "MAX_AUDIO", 8):
            self.assertEqual(413, (await self.client.post("/v1/audio/transcriptions", files={"file": ("large.wav", WAV)})).status_code)
        with patch.object(main, "MAX_JSON", 16):
            self.assertEqual(413, (await self.client.post("/v1/chat/completions", content="x" * 17)).status_code)

    async def test_partial_derivation_keeps_original_transcript(self):
        def handler(request):
            if request.url.path.endswith("/audio/asr/sse"):
                return httpx.Response(200, text=DONE)
            return httpx.Response(500, json={"error": "private body"})
        await self.setup_gateway(handler)
        response = await self.client.post("/api/process_voice", files={"file": ("source.wav", WAV)})
        self.assertEqual(202, response.status_code)
        self.assertEqual("周五交付原稿。", response.json()["transcription"])
        self.assertIsNone(response.json()["derived"])
        self.assertEqual("DERIVATION_FAILED", response.json()["processing_state"])

    async def test_actions_require_exact_quote_and_raw_never_replaced(self):
        def handler(request):
            if request.url.path.endswith("/audio/asr/sse"):
                return httpx.Response(200, text=DONE)
            derived = {"title": "交付", "summary": "周五交付", "structured_transcript": "模型改写稿",
                       "action_items": [{"text": "交付", "quote": "周五交付原稿。"}, {"text": "编造事项", "quote": "不存在"}]}
            return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps(derived)}}]})
        await self.setup_gateway(handler)
        response = await self.client.post("/api/process_voice", files={"file": ("source.wav", WAV)})
        result = response.json()
        self.assertEqual("周五交付原稿。", result["transcription"])
        self.assertNotIn("structured_transcript", result["derived"])
        self.assertEqual(1, len(result["derived"]["action_items"]))
        self.assertEqual("CANDIDATE", result["derived"]["action_items"][0]["state"])

    async def test_generic_multipart_is_explicit_and_not_retried_as_sse(self):
        def handler(request):
            self.assertEqual("/v1/audio/transcriptions", request.url.path)
            self.assertIn(b"audio.aac", request.content)
            return httpx.Response(200, json={"text": "generic original"})
        await self.setup_gateway(handler, main.Settings(base_url="https://example.invalid/v1", upstream_key="test"))
        response = await self.client.post("/v1/audio/transcriptions", files={"file": ("audio.aac", b"aac data")})
        self.assertEqual("generic original", response.json()["text"])

    def test_stream_parser_and_lan_binding(self):
        self.assertEqual("周五交付原稿。", main.parse_sse(DONE))
        self.assertEqual("", main.parse_sse('data: {"type":"transcript.text.done","text":""}'))
        for bad in ["{}", 'data: {"type":"transcript.text.delta","delta":"half"}', 'data: {"type":"error"}']:
            with self.assertRaises(HTTPException):
                main.parse_sse(bad)
        with self.assertRaises(RuntimeError):
            main.Settings(host="0.0.0.0").validate()


if __name__ == "__main__":
    unittest.main()
