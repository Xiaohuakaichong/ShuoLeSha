"""Private, bounded OpenAI-compatible gateway. Audio and credentials are never logged."""
from __future__ import annotations

import base64
import binascii
from contextlib import asynccontextmanager
from dataclasses import dataclass
import json
import os
import secrets
from typing import Any
from urllib.parse import urlparse

import httpx
from fastapi import FastAPI, File, Form, Header, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse, StreamingResponse

MAX_AUDIO = 16 * 1024 * 1024
MAX_JSON = 24 * 1024 * 1024
MAX_RESPONSE = 4 * 1024 * 1024


@dataclass(frozen=True)
class Settings:
    base_url: str = "https://api.stepfun.com/step_plan/v1"
    upstream_key: str = ""
    gateway_token: str = ""
    host: str = "127.0.0.1"
    asr_model: str = "stepaudio-2.5-asr"
    llm_model: str = "step-router-v1"

    @classmethod
    def from_env(cls) -> Settings:
        return cls(os.getenv("STEPFUN_BASE_URL", cls.base_url).rstrip("/"),
                   os.getenv("STEPFUN_API_KEY", ""), os.getenv("GATEWAY_TOKEN", ""),
                   os.getenv("HOST", "127.0.0.1"),
                   os.getenv("DEFAULT_ASR_MODEL", cls.asr_model),
                   os.getenv("DEFAULT_LLM_MODEL", cls.llm_model))

    @property
    def step_plan(self) -> bool:
        url = urlparse(self.base_url)
        return url.hostname == "api.stepfun.com" and "/step_plan/" in url.path

    def validate(self) -> None:
        if self.host not in {"127.0.0.1", "localhost", "::1"} and not self.gateway_token:
            raise RuntimeError("LAN binding requires GATEWAY_TOKEN")
        url = urlparse(self.base_url)
        if url.scheme not in {"http", "https"} or not url.hostname or url.username or url.password:
            raise RuntimeError("Invalid upstream URL")


def credential(settings: Settings, authorization: str | None) -> str:
    supplied = authorization[7:].strip() if authorization and authorization.startswith("Bearer ") else ""
    if settings.gateway_token:
        if not secrets.compare_digest(supplied, settings.gateway_token):
            raise HTTPException(401, "Invalid gateway credential")
        token = settings.upstream_key
    else:
        token = supplied or settings.upstream_key
    if not token:
        raise HTTPException(401, "Upstream credential is not configured")
    return token


async def json_body(request: Request) -> dict[str, Any]:
    content = bytearray()
    async for chunk in request.stream():
        content.extend(chunk)
        if len(content) > MAX_JSON:
            raise HTTPException(413, "Request is too large")
    try:
        body = json.loads(content)
        if not isinstance(body, dict):
            raise ValueError()
        return body
    except (ValueError, UnicodeError):
        raise HTTPException(400, "Invalid JSON object") from None


async def read_audio(file: UploadFile) -> bytes:
    content = bytearray()
    try:
        while chunk := await file.read(256 * 1024):
            content.extend(chunk)
            if len(content) > MAX_AUDIO:
                raise HTTPException(413, "Audio exceeds 16 MiB; split it first")
        if not content:
            raise HTTPException(400, "Audio is empty")
        return bytes(content)
    finally:
        await file.close()


def audio_format(filename: str, content: bytes) -> str:
    if content.startswith(b"RIFF") and content[8:12] == b"WAVE":
        return "wav"
    if content.startswith(b"OggS"):
        return "ogg"
    if content.startswith(b"ID3") or (len(content) >= 2 and content[0] == 0xFF and content[1] & 0xE0 == 0xE0 and content[1] & 0x16 != 0x10):
        return "mp3"
    if filename.lower().endswith(".pcm"):
        return "pcm"
    raise HTTPException(415, "Step Plan accepts WAV, MP3, OGG or PCM. Convert ADTS AAC to WAV first.")


def step_payload(content: bytes, kind: str, model: str) -> dict[str, Any]:
    fmt: dict[str, Any] = {"type": kind}
    if kind == "pcm":
        fmt.update(codec="pcm_s16le", rate=16000, bits=16, channel=1)
    return {"audio": {"data": base64.b64encode(content).decode("ascii"),
                      "input": {"transcription": {"model": model, "language": "zh", "enable_itn": True}, "format": fmt}}}


def validate_step_body(body: dict[str, Any]) -> None:
    try:
        audio = body["audio"]
        encoded = audio["data"]
        if not isinstance(encoded, str) or len(encoded) > (MAX_AUDIO + 2) // 3 * 4:
            raise HTTPException(413, "Audio is too large")
        content = base64.b64decode(encoded, validate=True)
        if not content or len(content) > MAX_AUDIO:
            raise HTTPException(400, "Audio is empty or too large")
        kind = audio["input"]["format"]["type"]
        if kind not in {"wav", "mp3", "ogg", "pcm"}:
            raise HTTPException(415, "Unsupported Step Plan audio format")
        if not isinstance(audio["input"]["transcription"]["model"], str):
            raise ValueError()
    except (KeyError, TypeError, ValueError, binascii.Error):
        raise HTTPException(400, "Expected nested audio.data and audio.input payload") from None


def parse_sse(text: str) -> str:
    final: str | None = None
    deltas: list[str] = []
    recognized = False
    complete = False
    for line in text.splitlines():
        if not line.startswith("data:"):
            continue
        data = line.removeprefix("data:").strip()
        if data == "[DONE]":
            complete = True
            continue
        try:
            event = json.loads(data)
        except ValueError:
            continue
        kind = event.get("type") if isinstance(event, dict) else None
        if kind == "error" or isinstance(event, dict) and "error" in event:
            raise HTTPException(502, "Upstream transcription failed")
        if kind == "transcript.text.done" and isinstance(event.get("text"), str):
            final = event["text"]
            recognized = complete = True
        elif kind == "transcript.text.delta" and isinstance(event.get("delta"), str):
            deltas.append(event["delta"])
            recognized = True
    if not recognized or not complete:
        raise HTTPException(502, "Incomplete or invalid transcription stream")
    return (final if final is not None else "".join(deltas)).strip()


async def upstream(client: httpx.AsyncClient, settings: Settings, token: str, path: str, **kwargs: Any) -> httpx.Response:
    try:
        request = client.build_request("POST", settings.base_url + path, headers={"Authorization": f"Bearer {token}"}, **kwargs)
        response = await client.send(request, stream=True)
    except httpx.TimeoutException:
        raise HTTPException(504, "Upstream timed out") from None
    except httpx.HTTPError:
        raise HTTPException(502, "Upstream is unavailable") from None
    if not 200 <= response.status_code < 300:
        status = response.status_code
        await response.aclose()
        raise HTTPException(status if 400 <= status <= 599 else 502, "Upstream request rejected")
    return response


async def bounded_response(response: httpx.Response) -> bytes:
    data = bytearray()
    try:
        async for chunk in response.aiter_bytes():
            data.extend(chunk)
            if len(data) > MAX_RESPONSE:
                raise HTTPException(502, "Upstream response is too large")
        return bytes(data)
    except httpx.TimeoutException:
        raise HTTPException(504, "Upstream timed out") from None
    except httpx.HTTPError:
        raise HTTPException(502, "Upstream response was interrupted") from None
    finally:
        await response.aclose()


async def response_json(response: httpx.Response) -> dict[str, Any]:
    try:
        value = json.loads(await bounded_response(response))
        if not isinstance(value, dict):
            raise ValueError()
        return value
    except (ValueError, UnicodeError):
        raise HTTPException(502, "Invalid upstream JSON response") from None


async def transcribe(client: httpx.AsyncClient, settings: Settings, token: str, content: bytes, filename: str, model: str) -> str:
    if settings.step_plan:
        response = await upstream(client, settings, token, "/audio/asr/sse", json=step_payload(content, audio_format(filename, content), model))
        data = await bounded_response(response)
        return parse_sse(data.decode("utf-8"))
    response = await upstream(client, settings, token, "/audio/transcriptions",
                              files={"file": (os.path.basename(filename), content, "application/octet-stream")},
                              data={"model": model, "response_format": "json"})
    result = await response_json(response)
    if not isinstance(result.get("text"), str):
        raise HTTPException(502, "Transcription response has no text field")
    return result["text"].strip()


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings.from_env()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        settings.validate()
        async with httpx.AsyncClient(timeout=httpx.Timeout(180, connect=20), follow_redirects=False,
                                     limits=httpx.Limits(max_connections=16, max_keepalive_connections=8)) as client:
            app.state.client = client
            yield

    app = FastAPI(title="说了啥私有网关", version="4.0", lifespan=lifespan)

    @app.middleware("http")
    async def size_guard(request: Request, call_next):
        if request.client and request.client.host not in {"127.0.0.1", "::1", "localhost", "testclient"} and not settings.gateway_token:
            return JSONResponse({"detail": "Remote access requires a gateway credential"}, status_code=403)
        length = request.headers.get("content-length", "")
        if length.isdigit() and int(length) > MAX_JSON:
            return JSONResponse({"detail": "Request is too large"}, status_code=413)
        if request.method == "POST":
            data = bytearray()
            async for chunk in request.stream():
                data.extend(chunk)
                if len(data) > MAX_JSON:
                    return JSONResponse({"detail": "Request is too large"}, status_code=413)
            request._body = bytes(data)
        return await call_next(request)

    @app.get("/")
    @app.get("/health")
    async def health():
        return {"service": "ShuoLeSha", "status": "ok", "version": "4.0"}

    @app.post("/v1/audio/asr/sse")
    async def asr_sse(request: Request, authorization: str | None = Header(None)):
        token = credential(settings, authorization)
        body = await json_body(request)
        validate_step_body(body)
        response = await upstream(app.state.client, settings, token, "/audio/asr/sse", json=body)
        async def stream():
            size = 0
            try:
                async for chunk in response.aiter_bytes():
                    size += len(chunk)
                    if size > MAX_RESPONSE:
                        yield b'data: {"type":"error","message":"response limit"}\n\n'
                        return
                    yield chunk
            except httpx.HTTPError:
                yield b'data: {"type":"error","message":"upstream interrupted"}\n\n'
            finally:
                await response.aclose()
        return StreamingResponse(stream(), media_type="text/event-stream", headers={"Cache-Control": "no-cache"})

    @app.post("/v1/audio/transcriptions")
    async def transcription(file: UploadFile = File(...), model: str = Form(settings.asr_model), authorization: str | None = Header(None)):
        token = credential(settings, authorization)
        name = file.filename or "audio"
        content = await read_audio(file)
        text = await transcribe(app.state.client, settings, token, content, name, model)
        return {"text": text}

    @app.post("/v1/chat/completions")
    async def chat(request: Request, authorization: str | None = Header(None)):
        token = credential(settings, authorization)
        body = await json_body(request)
        if body.get("stream"):
            raise HTTPException(400, "This endpoint accepts non-streaming chat requests")
        if not isinstance(body.get("messages"), list) or not body.get("model"):
            raise HTTPException(400, "Model and messages are required")
        response = await upstream(app.state.client, settings, token, "/chat/completions", json=body)
        return await response_json(response)

    @app.post("/api/process_voice")
    async def process_voice(file: UploadFile = File(...), asr_model: str = Form(settings.asr_model),
                            llm_model: str = Form(settings.llm_model), authorization: str | None = Header(None)):
        token = credential(settings, authorization)
        name = file.filename or "audio"
        content = await read_audio(file)
        raw = await transcribe(app.state.client, settings, token, content, name, asr_model)
        if not raw:
            return {"transcription": "", "processing_state": "SILENCE", "derived": None}
        payload = {"model": llm_model, "temperature": 0.1, "response_format": {"type": "json_object"},
                   "messages": [{"role": "system", "content": '只依据原稿输出 JSON：{"title":"标题","summary":"事实要点","action_items":[{"text":"明确承诺","quote":"逐字原句"}]}。不推断真人身份，不补充新待办。'},
                                {"role": "user", "content": raw}]}
        try:
            response = await upstream(app.state.client, settings, token, "/chat/completions", json=payload)
            body = await response_json(response)
            derived = json.loads(body["choices"][0]["message"]["content"])
            if not isinstance(derived, dict) or not isinstance(derived.get("summary"), str):
                raise ValueError()
            items = derived.get("action_items", [])
            if not isinstance(items, list):
                raise ValueError()
            derived["action_items"] = [dict(item, state="CANDIDATE") for item in items if isinstance(item, dict)
                                       and isinstance(item.get("text"), str) and isinstance(item.get("quote"), str)
                                       and item["quote"] and item["quote"] in raw]
            derived.pop("structured_transcript", None)
            return {"transcription": raw, "processing_state": "READY", "derived": derived}
        except (HTTPException, ValueError, KeyError, IndexError, TypeError):
            return JSONResponse({"transcription": raw, "processing_state": "DERIVATION_FAILED", "derived": None,
                                 "error": "Original transcript preserved; retry derivation separately"}, status_code=202)

    return app


app = create_app()

if __name__ == "__main__":
    import uvicorn
    config = Settings.from_env()
    config.validate()
    uvicorn.run(app, host=config.host, port=int(os.getenv("PORT", "8000")))
