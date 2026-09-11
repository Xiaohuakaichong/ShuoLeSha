# v3.0 阶段 4：死代码与重复入口清理

日期：2026-09-11  
分支：`feat/v3.0-rewrite`

## 已删除 / 降级

- 删除 `ui/components/LifeLogSheet.kt`（记录页里 `showLifeLogSheet` 从未为 true）
- 记录页不再放「去生成复盘」横幅，复盘只走「今日」Tab
- 底栏不再放设置，去掉主页级模式切换入口
- 列表卡片不再展开逐字稿、不再放播放按钮，避免和纪要页重复
- UI 文案「LifeLog 模式」改为「随身」；数据字段 `recordingMode=lifelog` 与 tag `LifeLog` 保留，后端协议不动

## 保留

- `RecordingModeSelectSheet`：仅在设置选择「每次选择」时弹出
- 快捷方式 / Quick Tile / 音量键盲操
- `server/` 未改

## 未在本阶段删除

- `data/model/LifeLogModel.kt` 与 `ApiService.generateLifeLogSummary`：今日复盘仍依赖
- 偏好键名 `lifelog_*`：避免迁移风险
