# 说了啥 (ShuoLeSha) - 项目记忆与开发准则

## 1. 迭代版本号管理规范 (强制执行)
- **每次开发与需求迭代，必须同步更新版本号**：
  - 更新 `app/build.gradle.kts`：
    - `versionCode`: 每次迭代严格自增 `+1`。
    - `versionName`: 采用语义化版本规范。
  - **版本号粒度与分级策略**：
    - **小迭代 / 体验微调 / Bug修补 (Patch)**：**必须使用更小的一级版本号**（多加一级，采用三级格式），例如从 `2.1` 升级至 `2.1.1`、`2.1.2` 等。
    - **中型功能新增 / 模块演进 (Minor)**：升级二级版本号，例如从 `2.1` 升级至 `2.2`。
    - **重大架构重构 / 核心视觉重塑 (Major)**：升级一级版本号，例如升级至 `3.0`。
  - **UI 页面同步更新**：
    - `NodeSettingsScreen.kt`（设置页关于卡片版本徽标，如 `v2.1.1`）及所有展示版本的地方必须同步更新，保持完全一致。

## 2. 代码提交与 GitHub 同步规范 (强制执行)
- 每次迭代开发、验证完成并生成安装包后，**必须立即同步到 GitHub**：
  - 远程仓库：`git@github.com:Xiaohuakaichong/ShuoLeSha.git`
  - 默认主分支：`origin/main`
  - 提交信息：采用清晰的 Conventional Commits 格式（如 `feat(v2.1.1): ...`、`fix(v2.1.2): ...`），并结构化附带改动要点清单。
  - 执行指令：完成提交后立即执行 `git push origin main`。

## 3. 构建、打包与真机部署规范
- **JDK 环境**：Windows 环境构建前必须预设 JDK 17：
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
  $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
  ```
- **Android SDK ADB 路径**：
  - `C:\Users\zjsxh\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- **安装包生成与子目录归档规范 (严禁输出至桌面)**：
  - **严禁输出或复制安装包到电脑桌面**，彻底杜绝桌面文件污染。
  - **统一收集整理存放于项目子文件夹**：`d:\Project\ShuoLeSha\apks\说了啥-v{versionName}.apk`。
- **真机静默覆盖安装**：
  - 检测到连接的测试真机（如 `R5CR91Q5YPF`）时，编译后自动通过 `adb -s <deviceId> install -r` 执行热覆盖安装。
