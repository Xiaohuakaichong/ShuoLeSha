import os
import json
import logging
from typing import Optional
from fastapi import FastAPI, UploadFile, File, Form, Header, HTTPException, Request
from fastapi.responses import JSONResponse
import httpx
import uvicorn

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("shuolesa-server")

app = FastAPI(
    title="说了啥 (ShuoLeSa) 私有中转服务",
    description="支持 OpenAI 规范的 ASR 语音识别、LLM 笔记提炼与 StepFun 智能代理中转",
    version="1.0.0",
)

# 默认配置（可通过环境变量或 .env 覆盖）
STEPFUN_API_KEY = os.getenv("STEPFUN_API_KEY", "")
STEPFUN_BASE_URL = os.getenv("STEPFUN_BASE_URL", "https://api.stepfun.com/v1").rstrip("/")
DEFAULT_ASR_MODEL = os.getenv("DEFAULT_ASR_MODEL", "stepaudio-2.5-asr")
DEFAULT_LLM_MODEL = os.getenv("DEFAULT_LLM_MODEL", "step-router-v1")

@app.get("/")
def index():
    return {
        "service": "ShuoLeSa Backend Gateway",
        "status": "online",
        "endpoints": [
            "/v1/audio/transcriptions",
            "/v1/chat/completions",
            "/api/process_voice",
        ],
    }

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/v1/audio/asr/sse")
async def transcribe_audio_sse(
    request: Request,
    authorization: Optional[str] = Header(None),
):
    """
    StepFun 专属 SSE 语音识别端点代理
    """
    token = authorization.replace("Bearer ", "").strip() if authorization else STEPFUN_API_KEY
    if not token:
        raise HTTPException(status_code=401, detail="Missing API Key")

    body = await request.json()
    target_url = f"{STEPFUN_BASE_URL}/audio/asr/sse"
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
    }

    client = httpx.AsyncClient(timeout=180.0)
    req = client.build_request("POST", target_url, headers=headers, json=body)
    resp = await client.send(req, stream=True)
    from fastapi.responses import StreamingResponse
    return StreamingResponse(resp.aiter_raw(), media_type="text/event-stream")

@app.post("/v1/audio/transcriptions")
async def transcribe_audio(
    file: UploadFile = File(...),
    model: str = Form(DEFAULT_ASR_MODEL),
    authorization: Optional[str] = Header(None),
):
    """
    OpenAI 兼容语音识别端点：
    直接中转至 StepFun 或其他上游 ASR 服务。
    """
    token = authorization.replace("Bearer ", "").strip() if authorization else STEPFUN_API_KEY
    if not token:
        raise HTTPException(status_code=401, detail="Missing API Key. Please provide Authorization header or set STEPFUN_API_KEY")

    content = await file.read()
    logger.info(f"Received audio file: {file.filename}, size: {len(content)} bytes, model: {model}")

    target_url = f"{STEPFUN_BASE_URL}/audio/transcriptions"
    headers = {"Authorization": f"Bearer {token}"}
    files = {"file": (file.filename or "audio.opus", content, file.content_type or "audio/ogg")}
    data = {"model": model, "response_format": "json"}

    async with httpx.AsyncClient(timeout=180.0) as client:
        try:
            resp = await client.post(target_url, headers=headers, files=files, data=data)
            logger.info(f"StepFun ASR status: {resp.status_code}")
            return JSONResponse(status_code=resp.status_code, content=resp.json())
        except Exception as e:
            logger.error(f"Error forwarding transcription to StepFun: {e}")
            raise HTTPException(status_code=500, detail=f"ASR Proxy error: {str(e)}")

@app.post("/v1/chat/completions")
async def chat_completions(request: Request, authorization: Optional[str] = Header(None)):
    """
    OpenAI 兼容聊天补全端点：
    直接透传至 StepFun 或其他兼容 LLM 模型。
    """
    token = authorization.replace("Bearer ", "").strip() if authorization else STEPFUN_API_KEY
    if not token:
        raise HTTPException(status_code=401, detail="Missing API Key. Please provide Authorization header or set STEPFUN_API_KEY")

    body = await request.json()
    logger.info(f"Received chat completion request for model: {body.get('model')}")

    target_url = f"{STEPFUN_BASE_URL}/chat/completions"
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }

    async with httpx.AsyncClient(timeout=180.0) as client:
        try:
            resp = await client.post(target_url, headers=headers, json=body)
            return JSONResponse(status_code=resp.status_code, content=resp.json())
        except Exception as e:
            logger.error(f"Error forwarding chat completion: {e}")
            raise HTTPException(status_code=500, detail=f"LLM Proxy error: {str(e)}")

@app.post("/api/process_voice")
async def process_voice_one_stop(
    file: UploadFile = File(...),
    asr_model: str = Form(DEFAULT_ASR_MODEL),
    llm_model: str = Form(DEFAULT_LLM_MODEL),
    authorization: Optional[str] = Header(None),
):
    """
    一站式处理接口：
    一次请求即可完成：音频转写 (优先走 SSE) -> LLM 结构化提取 (title, summary, action_items, tags)。
    """
    token = authorization.replace("Bearer ", "").strip() if authorization else STEPFUN_API_KEY
    if not token:
        raise HTTPException(status_code=401, detail="Missing API Key")

    content = await file.read()
    import base64
    base64_audio = base64.b64encode(content).decode("utf-8")

    # Step 1: Transcribe via SSE
    target_asr_url = f"{STEPFUN_BASE_URL}/audio/asr/sse"
    sse_payload = {
        "model": asr_model,
        "audio": base64_audio,
        "format": {"type": "ogg"},
        "enable_itn": True,
    }
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
    }

    transcription = ""
    async with httpx.AsyncClient(timeout=180.0) as client:
        try:
            async with client.stream("POST", target_asr_url, headers=headers, json=sse_payload) as resp:
                if resp.status_code == 200:
                    text_parts = []
                    async for line in resp.aiter_lines():
                        if line.startswith("data:"):
                            data_str = line.removePrefix("data:").strip()
                            if data_str == "[DONE]":
                                break
                            try:
                                j = json.loads(data_str)
                                if "data" in j:
                                    text_parts.append(j["data"])
                                elif "text" in j:
                                    transcription = j["text"]
                            except Exception:
                                pass
                    if not transcription:
                        transcription = "".join(text_parts).strip()
        except Exception as e:
            logger.error(f"SSE ASR failed, trying fallback: {e}")

    # Fallback to standard multipart if SSE was empty
    if not transcription:
        target_multipart_url = f"{STEPFUN_BASE_URL}/audio/transcriptions"
        headers_multi = {"Authorization": f"Bearer {token}"}
        files = {"file": (file.filename or "audio.opus", content, file.content_type or "audio/ogg")}
        data = {"model": asr_model, "response_format": "json"}
        async with httpx.AsyncClient(timeout=180.0) as client:
            asr_resp = await client.post(target_multipart_url, headers=headers_multi, files=files, data=data)
            if asr_resp.status_code == 200:
                transcription = asr_resp.json().get("text", "")

    if not transcription:
        raise HTTPException(status_code=400, detail="Audio transcription returned empty text")

    # Step 2: LLM Structuring with Speaker Inference & Action Items
    system_prompt = """你是一个专业的随身语音秘书与会议纪要分析专家。
请根据录音文字稿，若语境呈现多人交谈、访谈或会议，请智能推断说话人角色（如【发言人A】、【发言人B】或称呼），并将文字整理为对白或分段。
请输出标准合法的纯 JSON 字符串，格式如下：
{
  "title": "简明凝练的标题（12字以内）",
  "summary": "核心要点提炼（2-3句话，清晰明了）",
  "action_items": [
    {"text": "待办事项1", "done": false},
    {"text": "待办事项2", "done": false}
  ],
  "tags": ["工作", "日常"],
  "structured_transcript": "【发言人A】：...\n\n【发言人B】：..."
}
注意：只输出合法 JSON，不要带有 markdown 标记或任何闲聊文本。"""

    llm_payload = {
        "model": llm_model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": f"录音文字稿：\n{transcription}"},
        ],
        "temperature": 0.2,
    }

    async with httpx.AsyncClient(timeout=180.0) as client:
        try:
            llm_resp = await client.post(
                f"{STEPFUN_BASE_URL}/chat/completions",
                headers=headers,
                json=llm_payload,
            )
            if llm_resp.status_code != 200:
                return {
                    "transcription": transcription,
                    "summary": transcription,
                    "title": "随手语音",
                    "action_items": [],
                    "tags": ["语音"],
                    "structured_transcript": transcription,
                }

            llm_content = llm_resp.json()["choices"][0]["message"]["content"]
            clean_json = llm_content.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()

            try:
                parsed = json.loads(clean_json)
            except Exception:
                parsed = {
                    "title": "随手语音",
                    "summary": clean_json,
                    "action_items": [],
                    "tags": ["语音"],
                    "structured_transcript": transcription,
                }

            if "structured_transcript" not in parsed or not parsed["structured_transcript"]:
                parsed["structured_transcript"] = transcription
            parsed["transcription"] = parsed["structured_transcript"]
            return parsed
        except Exception as e:
            logger.error(f"Error in LLM processing: {e}")
            return {
                "transcription": transcription,
                "summary": transcription,
                "title": "随手语音",
                "action_items": [],
                "tags": ["语音"],
                "structured_transcript": transcription,
            }

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
