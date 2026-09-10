# “说了啥” 本地/私有后端中转服务

本服务基于 **FastAPI** 构建，提供了与 OpenAI 协议兼容的标准接口，适配“说了啥” Android 客户端。

## 功能特性
1. **OpenAI 兼容 ASR 接口**：`POST /v1/audio/transcriptions`（支持 StepFun、本地 Whisper、SenseVoice 等）
2. **OpenAI 兼容 LLM 接口**：`POST /v1/chat/completions`（支持 StepFun `step-1-8k` / `step-2-16k` 等）
3. **一站式提取接口**：`POST /api/process_voice`（上传音频直接返回转写、标题、总结、待办、标签）

## 快速启动

### 1. 本地 Python 启动
```bash
cd server
pip install -r requirements.txt

# 设置环境变量（可选，也可由手机端 Header 传入）
set STEPFUN_API_KEY=your_stepfun_api_key_here

python main.py
```
服务将在 `http://0.0.0.0:8000` 启动。

### 2. 手机端连接配置
* **在同一 WiFi 局域网下**：
  * 查看你电脑的局域网 IP（例如 `192.168.1.100`）；
  * 在“说了啥” App 设置中，切换提供商为 **本地服务 / 自定义 OpenAI**；
  * 将 Base URL 修改为：`http://192.168.1.100:8000/v1`；
  * 点击“测试连接”，验证通过即可无缝使用。
* **在外出（4G/5G）场景**：
  * 建议在手机 App 中直接切换为 **阶跃星辰 (StepFun) 直连模式**，输入你的 StepFun API Key，无需本地电脑开机。
