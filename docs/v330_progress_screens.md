# v3.0 阶段 3：关键页面重做

日期：2026-09-11  
分支：`feat/v3.0-rewrite`

## 目标

记录流不再当垃圾桶；纪要页内容优先；今日是复盘；待办是行动中心。

## 落地

### 记录（TimelineScreen + TimelineItem）
- 去掉生活手记横幅和不可达 LifeLogSheet
- 复盘记录不进记录流
- 筛选改为 全部 / 随身 / 会议 / 处理中
- 卡片只保留时间、模式、标题、两行摘要、待办计数
- 导入收成顶栏图标，不再和录音键抢入口

### 纪要（AudioPlayerScreen）
- 标题 + 摘要先行，待办与转录随后
- AI 工具和技术信息默认折叠
- 复盘记录或无音频文件时不展示播放器
- 底部播放条仅在有本地音频时出现

### 今日（LifeLogScreen）
- 一级入口改名为「今日」
- 去掉 emoji 堆砌，模式色用丁香紫
- 生成逻辑与后端协议不变

### 待办（TasksScreen）
- 默认看「进行中」
- 来源徽章用随身 / 会议 / 复盘，不再用 emoji 标题

### 录音中 / 设置
- 录音中模式标签改为随身 / 会议
- 设置分组：录音 / 盲操 / 引擎 / 关于，版本徽标 `v3.0`

## 改动文件

- `ui/screens/TimelineScreen.kt`
- `ui/components/TimelineItem.kt`
- `ui/screens/AudioPlayerScreen.kt`
- `ui/screens/LifeLogScreen.kt`
- `ui/screens/TasksScreen.kt`
- `ui/screens/ActiveRecordingScreen.kt`
- `ui/screens/NodeSettingsScreen.kt`
- `service/AudioCaptureService.kt`（仅展示文案）
