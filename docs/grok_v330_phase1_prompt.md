# ShuoLeSha 3.0 Phase 1 Prompt for Grok

## 目标
只做 Phase 1：统一设计系统，不动页面逻辑，不提交 GitHub。

## 分支
- 工作分支：`feat/v3.0-rewrite`
- 不要动 `main`
- 不要 `git commit` / `git push`

## 输入文件
- `app/src/main/java/com/example/shuolesa/theme/Color.kt`
- `app/src/main/java/com/example/shuolesa/theme/Dimens.kt`
- `app/src/main/java/com/example/shuolesa/theme/Type.kt`
- `app/src/main/java/com/example/shuolesa/theme/Theme.kt`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/xml/shortcuts.xml`

## 当前问题
1. 颜色是“终端霓虹 + 生活手记”两套人格混用
2. 间距/圆角/安全区 token 不完整，页面各自补 padding
3. 字号体系基本可用，但 label/caption 语义混用
4. Compose 主题和系统 `themes.xml` 不一致
5. shortcuts 视觉风格和 App 主题脱节

## 交付要求
1. 重写 `Color.kt`
   - 保留品牌色，但降低饱和/发光感
   - 新增清晰语义色：primary / secondary / tertiary / surface / background / outline / success / warning / danger
   - 保留必要 alias，避免全项目爆炸式修改

2. 重写 `Dimens.kt`
   - 统一：page padding / gap / card radius / field radius / button height / icon button / status dot / bottom nav / FAB / border
   - 明确 `bottomNavHeight`、`fabSize`、`statusBarClearance`、`navigationBarClearance`

3. 重写 `Type.kt`
   - 保留现有层级，但统一 label / caption 的字体、颜色、字重
   - 减少页面里临时拼凑的 TextStyle

4. 重写 `Theme.kt`
   - 使用新的 DarkColorScheme
   - 保证 Compose 主题与 `themes.xml` 协调

5. 轻改 `themes.xml`
   - 至少让系统层主题色与 Compose primary 一致
   - 不要大改 AndroidManifest/样式继承链

6. 检查 `shortcuts.xml`
   - 不改功能，只确认风格/命名是否需要跟随新主题

7. 输出进度文档 `docs/v330_progress_theme.md`
   - 改动文件清单
   - 未完成事项
   - 下一步建议

## 约束
- 不要动 `app/src/main/java/com/example/shuolesa/ui` 下页面文件
- 不要动 `server/`
- 不要 `git commit` / `git push`
- 优先保证代码可编译
- 如果某文件只需要局部改，优先 patch；如果结构已不适用，直接覆盖写入
