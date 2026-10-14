# 说了啥私有网关 4.0

手机可以直连服务商；网关是可选项。默认绑定 `127.0.0.1:8000`，使用共享异步 HTTP 客户端，音频限制 16 MiB，完整请求限制 24 MiB，响应限制 4 MiB。日志不记录密钥、录音、原稿或服务商响应体。

使用 Python 3.11+。本次验证环境为 Windows、Python 3.14；可使用已验证的锁定依赖：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r server/requirements-lock.txt
$env:STEPFUN_API_KEY = '<在本机填写上游密钥>'
.\.venv\Scripts\python.exe server/main.py
```

`STEPFUN_BASE_URL` 默认 `https://api.stepfun.com/step_plan/v1`。该地址显式采用嵌套 `audio.data`、`audio.input.transcription`、`audio.input.format` 的 SSE 转写协议。上传 AAC 时应先转成 WAV，禁止把 ADTS 裸流标成 OGG 或 M4A。其他地址显式采用 `/audio/transcriptions` multipart 接口。

手机访问本机网关需要局域网绑定和独立网关凭据：

```powershell
$env:HOST = '0.0.0.0'
$env:GATEWAY_TOKEN = '<在本机生成独立随机凭据>'
$env:STEPFUN_API_KEY = '<上游密钥>'
.\.venv\Scripts\python.exe server/main.py
```

手机选择“本地 / 自定义”，地址填 `http://<电脑局域网IP>:8000/v1`，密钥填 `GATEWAY_TOKEN`。使用上述阶跃配置时，手机转写模型填 `stepaudio-2.5-asr`，整理模型填 `step-router-v1`；连接自己的 Whisper 或其他服务时填写该服务的模型名。网关使用恒定时间比较校验网关凭据，上游仅接收 `STEPFUN_API_KEY`。不设置网关凭据时拒绝非回环客户端；CLI 也禁止未配置凭据的局域网绑定。不要把服务直接暴露到公网；远程使用时在前方配置 HTTPS。

接口：

- `GET /health`：运行状态。
- `POST /v1/audio/transcriptions`：标准 multipart，返回原始 `text`。
- `POST /v1/audio/asr/sse`：嵌套 Step Plan 请求。转发结束、异常、客户端关闭时释放上游连接；错误 HTTP 状态在开始推流前返回。
- `POST /v1/chat/completions`：非流式兼容聊天接口。
- `POST /api/process_voice`：兼容旧调用者。`transcription` 始终是原始转写，`derived` 单独保存整理结果；整理失败返回 HTTP 202、`DERIVATION_FAILED` 和保留的原稿。行动项必须含逐字来源，状态为 `CANDIDATE`。

运行模拟上游测试，不需要密钥，也不发送真实录音：

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s server -v
```

`requirements.txt` 给出支持范围，`requirements-lock.txt` 固定本次通过测试的依赖版本。
