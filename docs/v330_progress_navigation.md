# v3.0 阶段 2：导航与主入口

日期：2026-09-11  
分支：`feat/v3.0-rewrite`

## 目标

底部导航从 4 等分改为 3 个主 Tab + 常驻录音键；设置降为二级页。

## 落地

- 主 Tab：`记录` / `今日` / `待办`
- 底栏中央常驻圆形录音键，三 Tab 都看得到
- 设置从底栏移除，改由各主页顶栏齿轮进入，全屏二级页带回退
- 录音中 / 纪要页 / 设置页隐藏底栏
- 模式选择 Sheet 文案改为「随身 / 会议」，协议值仍是 `lifelog` / `meeting`

## 改动文件

- `ui/navigation/AppNavigation.kt`
- `ui/components/RecordingModeSelectSheet.kt`
- `ui/screens/NodeSettingsScreen.kt`（增加 `onBack`，作为二级页）
- `ui/screens/TimelineScreen.kt` / `LifeLogScreen.kt` / `TasksScreen.kt`（增加 `onOpenSettings`）

## 下一阶段

重做记录流、纪要页、今日复盘、待办中心。
