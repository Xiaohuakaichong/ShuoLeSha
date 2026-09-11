# ShuoLeSha (说了啥) - UI/UX Review Request

## 1. 项目概述
- 项目名称：说了啥 (ShuoLeSha)
- 当前版本：v2.1.5 (versionCode 8)
- 技术栈：Android (minSdk 29, targetSdk 36), Kotlin, Jetpack Compose, Material 3, Room, WorkManager, DataStore
- 后端：FastAPI 私有中转服务，OpenAI 兼容接口，支持 StepFun ASR/LLM
- Git 仓库：git@github.com:Xiaohuakaichong/ShuoLeSha.git
- 当前分支：main
- 远程：origin/main

## 2. 当前功能现状
根据 git 历史与代码结构，当前版本已具备：
- 录音/转写：支持系统音频捕获、Opus/WAV 录音、OpenAI 兼容 ASR
- 本地中转后端：`server/main.py` 提供 `/v1/audio/transcriptions`、`/v1/chat/completions`、`/api/process_voice`
- 笔记提炼：LLM 自动生成 title/summary/action_items/tags/structured_transcript
- 多工作台：Timeline、Tasks、LifeLog、AudioPlayer、ActiveRecording、NodeSettings
- 盲操/投屏：QuickTile、BlindTrigger、scrcpy 镜像脚本
- 导入/导出与诊断：日志导出、分享、运行诊断

## 3. 近期迭代轨迹（关键 commits）
- `feat(v2.1.5)` 修复录音排队卡顿，新增诊断报告与日志导出/分享
- `feat(v2.1.4)` 动态化录音界面模式音质展示，LifeLog/会议区分标识
- `fix(v2.1.3)` 统一全量页面顶部留白高度，移除主页副标题
- `fix(v2.1.2)` 移除主页冗余模式切换按钮，优化导入按钮与 Tab 排版折行
- `fix(v2.1.1)` 设置页拆分为 4-Tab，修复按钮排版与顶部安全区
- `feat(v2.1)` 升级音频品质分级、4栏工作台、LifeLog 生活手记与双阶盲操长按

## 4. 关键代码入口
- `app/src/main/java/com/example/shuolesa/MainActivity.kt`
- `app/src/main/java/com/example/shuolesa/ui/navigation/AppNavigation.kt`
- `app/src/main/java/com/example/shuolesa/ui/screens/TimelineScreen.kt`
- `app/src/main/java/com/example/shuolesa/ui/screens/LifeLogScreen.kt`
- `app/src/main/java/com/example/shuolesa/ui/screens/TasksScreen.kt`
- `app/src/main/java/com/example/shuolesa/ui/screens/NodeSettingsScreen.kt`
- `app/src/main/java/com/example/shuolesa/ui/screens/ActiveRecordingScreen.kt`
- `app/src/main/java/com/example/shuolesa/ui/screens/AudioPlayerScreen.kt`
- `app/src/main/java/com/example/shuolesa/ui/components/TimelineItem.kt`
- `app/src/main/java/com/example/shuolesa/ui/components/LifeLogSheet.kt`
- `app/src/main/java/com/example/shuolesa/ui/components/RecordingModeSelectSheet.kt`
- `app/src/main/java/com/example/shuolesa/theme/Theme.kt`
- `app/src/main/java/com/example/shuolesa/theme/Color.kt`
- `app/src/main/java/com/example/shuolesa/theme/Type.kt`
- `app/src/main/java/com/example/shuolesa/data/model/LifeLogModel.kt`
- `app/src/main/java/com/example/shuolesa/data/model/ActionItemModel.kt`
- `app/src/main/java/com/example/shuolesa/data/repository/AudioRepository.kt`
- `app/src/main/java/com/example/shuolesa/network/ApiService.kt`
- `app/src/main/AndroidManifest.xml`

## 5. 已知痛点信号（来自版本记录与当前代码）
- 界面信息密度偏高，主屏入口与模式切换关系不够直观
- 顶部留白/安全区处理经历过多次修补，说明基础布局一致性不足
- 录音、转写、待办、LifeLog 四类信息并存，但视觉分层与任务主次不够稳定
- 设置页结构复杂，曾出现按钮排版与折行问题
- 导入/导出、诊断、投屏等能力很多，但入口组织略拥挤

## 6. 用户真实诉求
- 希望做一次完整 UI/UX review
- 明确表示当前界面“比较混乱”
- 目标不是小修小补，而是基于现有能力重新规划一套更清晰的 3.0 视觉与交互架构
- 保留核心能力：录音转写、LLM 提炼、待办、LifeLog、本地后端、投屏/盲操
- 先看 Grok 的发现与建议，确认后再开工，不动代码

## 7. Review 重点问题
1. 信息架构是否过度拥挤？
2. 录音流程是否足够聚焦？是否存在与转写结果页、待办页竞争主次？
3. LifeLog 与 Timeline 的关系是否让用户困惑？
4. 设置页是否承载了过多异构功能？
5. 3.0 版本是否需要重新定义主屏与底部导航？
6. 哪些页面/组件应该被折叠、降级或移除？
7. 视觉层面是否存在颜色、字体、密度不一致？

## 8. 交付要求
请输出：
1. 核心问题清单（按严重程度排序）
2. 交互/视觉层面的 5-10 条具体建议
3. 建议的 3.0 信息架构草案（可用文字/树状结构描述）
4. 建议的主页/底部导航/关键页布局方向
5. 是否需要引入新的设计系统变量或主题策略
6. 明确哪些能力建议保留、折叠、重做或移除

## 9. 约束
- 不涉及后端协议大改
- 不要求立即写代码，只输出 review 与架构建议
- 尊重项目现有 Git 流程：后续改动用独立 feature branch，不直接动 main
