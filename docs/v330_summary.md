# ShuoLeSha v3.0 重构汇总

日期：2026-09-11  
分支：`feat/v3.0-rewrite`（**未 commit / 未 push**，`main` 未动）  
版本：`versionCode 9` / `versionName 3.0`

## 做了什么

按 UI/UX review 把 2.1.5 的四栏工作台收成 3.0 信息架构：

1. **设计系统**：语义色三分（随身薄荷绿 / 会议电蓝 / 复盘丁香紫），压低霓虹饱和度；Dimens / Type / 暗色 `themes.xml` 对齐。
2. **导航**：底栏 `记录 | 今日 | 待办` + 中央常驻录音键；设置降为二级页。
3. **页面**：记录流只放录音结果；纪要内容优先；今日做复盘；待办默认看进行中。
4. **清理**：删除不可达的 `LifeLogSheet`；去掉记录页复盘横幅和卡片内展开/播放重复入口。
5. **协议**：`server/` 未改；`recordingMode=lifelog|meeting` 与 tag `LifeLog` 保留。

阶段文档：

- `docs/v330_progress_theme.md`
- `docs/v330_progress_navigation.md`
- `docs/v330_progress_screens.md`
- `docs/v330_progress_cleanup.md`

---

## 改动的文件清单

### 版本与主题
- `app/build.gradle.kts` — 9 / 3.0
- `theme/Color.kt` — `AppColor` 语义色 + 旧名兼容别名
- `theme/Dimens.kt`
- `theme/Type.kt`
- `theme/Theme.kt`
- `res/values/themes.xml`
- `res/values/colors.xml`（新增，与 `AppColor` 对齐）
- `res/drawable/ic_mic.xml`
- `res/drawable/ic_shortcut_record.xml`（新增）
- `res/xml/shortcuts.xml`

### 导航与组件
- `ui/navigation/AppNavigation.kt`
- `ui/components/TerminalComponents.kt` — `FilterChip` / `ModeBadge` / `SelectableTile` / 顶栏回退与设置
- `ui/components/TimelineItem.kt`
- `ui/components/RecordingModeSelectSheet.kt`
- `ui/components/PulsingDot.kt`
- `ui/components/LifeLogSheet.kt` — **已删除**

### 页面
- `ui/screens/TimelineScreen.kt` — 记录流
- `ui/screens/LifeLogScreen.kt` — 今日复盘
- `ui/screens/TasksScreen.kt` — 待办中心
- `ui/screens/AudioPlayerScreen.kt` — 纪要页
- `ui/screens/ActiveRecordingScreen.kt`
- `ui/screens/NodeSettingsScreen.kt` — 二级设置

### 展示文案（非协议）
- `data/db/AudioRecordEntity.kt` — 模式标签改为 随身 / 会议 / 复盘
- `service/AudioCaptureService.kt` — 录音中标题文案

### 文档
- `docs/v330_progress_*.md`
- `docs/v330_summary.md`

**未改**：`server/`、Room schema、DataStore key、ASR/LLM 请求体。

---

## 未完成项

- 真机交互走查未在本会话做完（安装成功，未逐屏点按验证）
- 设置页内部仍是 4 段 Tab，没有改成单页手风琴；文案已分组
- DataStore 键名仍是 `lifelog_*`，避免迁移
- 列表卡片尚未全部改用 `AppColor` 直调，多数走兼容别名
- 无障碍盲操 / Quick Tile / 桌面快捷方式未做专项回归
- 没有自动化 UI 测试

---

## 构建命令与结果

JDK：

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon
# BUILD SUCCESSFUL

.\gradlew.bat :app:assembleDebug --no-daemon
# BUILD SUCCESSFUL in 25s
```

安装包：

- 构建产物：`app/build/outputs/apk/debug/app-debug.apk`
- 归档：`apks/说了啥-v3.0.apk`

---

## 真机安装建议

测试机 `R5CR91Q5YPF` 已覆盖安装成功：

```powershell
& "C:\Users\zjsxh\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s R5CR91Q5YPF install -r "apks\说了啥-v3.0.apk"
# Performing Streamed Install
# Success
```

建议打开后按这个顺序看：

1. 底栏是否只有 记录 / 今日 / 待办，中央录音键是否常驻
2. 点齿轮进设置，返回是否回到原 Tab
3. 记录流不再出现复盘横幅；卡片点进去是纪要而不是内嵌展开
4. 今日生成复盘（有当天录音时）
5. 待办勾选是否同步回对应录音
6. 录音键：默认模式直接开录；设置成「每次选择」时弹出随身 / 会议

---

## 下一步建议

1. 真机过一遍录音 → 转写 → 纪要 → 待办 → 今日复盘闭环
2. 确认盲操两档仍分别打到 `lifelog` / `meeting`
3. 用户认可后再 `git commit`（不要直接推 `main`），例如：
   `feat(v3.0): 三栏导航与设计系统重构`
4. 若视觉还偏「终端」，继续把屏幕从兼容别名迁到 `AppColor` / `MaterialTheme.colorScheme`
5. 设置页可再收成单滚动分组，减少二级页里的第四层 Tab
