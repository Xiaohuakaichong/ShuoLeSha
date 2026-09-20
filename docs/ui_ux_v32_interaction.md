# v3.2 交互说明（重写版）

## 底栏

- 结构：`记录 | 今日 | 🎤 | 待办`
- 设置：各页右上角齿轮 → overlay
- 录音键：短按 = 启动策略；长按 = 随身/会议选择
- 几何：左右半宽对分，中间 Spacer = 录音键 + 16dp，FAB 落在空洞中心（2+🎤+1）

## 录音中

- 全屏进度台；返回 /「收起」= `moveTaskToBack`，服务继续
- 「完成并停止」= 危险色，停服务并 enqueue 上传
- 状态区：采集分片 / 上传 / 转写
  - `liveSessionId` / `liveChunkIndex` 在 `startSession` 后即更新
  - UI 用 **精确 sessionId** 过滤已入库分片
  - 上传行区分排队 / 上传中 / 完成 / 失败

## 停录后

- 切到「记录」，关闭设置/纪要 overlay
- 显示处理中横幅（约 12s 或手动关闭）
- 列表内 PENDING/UPLOADING 仍可见

## 记录

- 「搜」：FTS/LIKE（v3.1 `searchRecords`）
- 「问」：禁用输入 + 占位说明；不跑 FTS
- 空态：真空 → 指向底栏麦克风；筛选/搜索空 → 对应文案；**无第二录音大按钮**

## 今日

- 日期 chips：今天 / 昨天 / 前天（sticky）；生成中不可切日
- 上层：当天 N 段可点进纪要（`observeAllRecords` 实时刷新）
- 下层：复盘正文（含 Reminders / Notepad / Memory）
- 无片段：CTA「去录一段」；有片段才启用生成
- 筛选：**只** `!isDailyLifeLogSummary()`，不用 `tags.contains("LifeLog")`

## 待办

- ☐ 只切换完成态
- 行内容点击：片段 → 纪要；复盘 → 今日对应日
- 芯片：模式 · 标题 · 日期

## 设置 · 数据与信任

- 只手动开麦（说明）
- 当前 Base URL / 引擎摘要
- 本地原件保留（**唯一**开关；录音分组仅提示上溯）
- 权限行可点申请

## 返回语义

| 场景 | 行为 |
|---|---|
| 录音中 | `moveTaskToBack`，不停录 |
| 设置 / 纪要 overlay | 关闭 overlay |
| 根 Tab | 再按一次退出（2s 内） |
