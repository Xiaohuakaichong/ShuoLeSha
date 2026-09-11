# Phase 1 — 统一设计系统（主题层）

状态：主题层已落地，未提交 Git。页面逻辑未改。

目标：把「终端霓虹」收成一套温和克制的深色语义系统，品牌色（薄荷 / 蓝 / 紫）保留色相，降低饱和度与发光。

## 改动文件

| 文件 | 作用 |
|---|---|
| `app/src/main/java/com/example/shuolesa/theme/Color.kt` | 新增 `AppColor` 语义色；角色色与旧名别名都指向同一套值 |
| `app/src/main/java/com/example/shuolesa/theme/Dimens.kt` | 统一 spacing / radius / icon / 底栏 / FAB |
| `app/src/main/java/com/example/shuolesa/theme/Type.kt` | 保留字号层级；统一 label / caption 字体与颜色语义 |
| `app/src/main/java/com/example/shuolesa/theme/Theme.kt` | 切换到完整 `DarkColorScheme` |
| `app/src/main/res/values/colors.xml` | XML 侧与 `AppColor` 对齐的色值 |
| `app/src/main/res/values/themes.xml` | 从 Light 父主题改为深色系统主题，冷启动不再白闪 |
| `app/src/main/res/xml/shortcuts.xml` | 快捷方式图标改为主题色 |
| `app/src/main/res/drawable/ic_shortcut_record.xml` | 快捷方式麦克风图标，tint = `@color/primary` |

未改：`app/src/main/java/com/example/shuolesa/ui/**`、`server/**`。未 `git commit` / `git push`。

## 语义色（`AppColor`）

| Token | Hex | 用途 |
|---|---|---|
| `primary` | `#3CBFA8` | 品牌薄荷（原 `#00E5B8` 降饱和） |
| `secondary` | `#6B8FCF` | 会议 / 信息蓝（原 `#387BFF`） |
| `tertiary` | `#8B7CC4` | 复盘 / 次强调紫（原 `#8B5CF6`） |
| `surface` | `#141820` | 卡片、底栏 |
| `background` | `#0C0E12` | 页面底（保持原炭黑） |
| `outline` | `#2A3344` | 描边；hover 不再用高饱和蓝 |
| `success` | `#5BAF8A` | 已上传 / 完成 |
| `warning` | `#C9A24A` | 待处理 |
| `danger` | `#C97070` | 失败 / 停止 |

Glow 从 `0x33`（20%）降到 `0x1F`（约 12%），并绑到降饱和后的品牌色。

### 角色色（给已开始用新名字的组件）

- `Accent` / `AccentOn` / `AccentGlow` → primary
- `ModeCasual` → primary
- `ModeMeeting` → secondary
- `ModeDigest` → tertiary
- `modeColor(isMeeting)` / `recordRoleColor(isDigest, isMeeting)`

### 旧名别名（页面仍在 import，禁止在新代码继续扩散）

`MintCyan` / `NeonGreen` → primary；`ElectricBlue` → secondary；`PurpleAccent` → tertiary；`BgDark` / `CardDark` / `DangerRed` / `StatusUploaded` 等全部接到 `AppColor`。旧页面不改 import 也会变克制。

## 尺寸（`Dimens`）

- Spacing：`spaceXs…spaceXxl`（4 / 8 / 16 / 24 / 32 / 40），`gap*` 为同值别名
- Radius：`radiusXs…radiusFull`；`cardRadius` 12、`fieldRadius` 8、`chipRadius` 6、`sheetRadius` 16、`pillRadius` 20
- Icon：12 / 16 / 20 / 24 / 28 / 32
- 底栏：`bottomNavHeight` = `bottomBarHeight` = 80
- FAB / 录音键：`fabSize` = `recordButton` = 56，`fabIconSize` = 28
- 页面边距：`pagePaddingH` 20、`pagePaddingV` 16、`pageBottomNavClearance` 80

后续页面不要再手写 `1.dp` / `8.dp` / `RoundedCornerShape(6.dp)`。

## 字体（`Type`）

字号层级未改：36 / 26 / 20 / 17 / 15 / 13 / 11。

补了 `titleSmall`（14sp）。

label / caption 语义：

- 正文、标题：系统 Sans + `textPrimary` / `textSecondary` / `textMuted`
- `label*` 与 `Caption` / `CaptionStrong`：JetBrains Mono
- `labelLarge` 不再写死薄荷绿，改为 `textPrimary`；品牌色在调用处再上
- 时间戳 / 状态 / chip 用 `Caption`（10sp muted）或 `labelSmall`；强调 meta 用 `CaptionStrong` / `labelMedium`

## XML 与快捷方式

- `themes.xml` 父主题从 `Theme.Material.Light.NoActionBar` 改为 `Theme.Material.NoActionBar`，`windowBackground` / status / nav bar / `colorPrimary` 与 Compose 深色方案一致。
- `shortcuts.xml` 本身没有颜色属性。原 `@drawable/ic_mic` tint 仍是霓虹 `#39FF14`，快捷方式改为 `@drawable/ic_shortcut_record`（`@color/primary`）。

## 编译情况

主题文件本身可通过 Kotlin 编译。当前 `:app:compileDebugKotlin` 仍失败，原因在 **页面签名尚未对齐**（不在本 Phase）：

- `AppNavigation` 向 `LifeLogScreen` / `TasksScreen` 传了 `onOpenSettings`，这两个 Composable 还没有该参数
- `AppNavigation` 向 `NodeSettingsScreen` 传了 `onBack`，设置页还没有该参数
- `TimelineScreen` 使用了 `dp` 但未 import `androidx.compose.ui.unit.dp`

这些是信息架构改写中的半成品，不是主题层引入的。

## 尚未处理（后续 Phase）

1. **页面迁移到语义 token**：`LifeLogScreen` / `TasksScreen` / `AudioPlayerScreen` / `NodeSettingsScreen` / `LifeLogSheet` 仍大量写 `MintCyan` / `NeonGreen` / 临时 `fontSize` / 裸 `RoundedCornerShape`。
2. **底栏与录音键真正吃 Dimens**：`AppNavigation` 里录音键图标仍是 `28.dp`，上浮 `(-12).dp`，未用 `fabIconSize`。
3. **系统快捷图标残留霓虹**：`ic_mic.xml` 仍是 `#39FF14`（Quick Settings Tile 还在用）；`ic_stop.xml` 仍是 `#FF1744`。
4. **启动器图标**：`ic_launcher_background` 仍是 Android 默认绿 `#3DDC84`。
5. **发光动效**：`PulsingDot` 仍是光晕动画；颜色已接到降饱和 `AccentGlow`，动效强度未收。
6. **信息架构半成品**：设置降为二级页、三栏底栏、页面 `onOpenSettings` / `onBack` 签名需要对齐后才能整包编译。
7. **版本号**：`app/build.gradle.kts` 已在分支上被改成 `3.0` / `versionCode 9`（非本 Phase 写入）。设置页版本徽标等展示点需在后续同步。
8. **不提交**：本 Phase 按要求不 commit、不 push。

## 下一 Phase 建议用法

```kotlin
color = AppColor.primary          // 或 MaterialTheme.colorScheme.primary
color = AppColor.success
Modifier.padding(horizontal = Dimens.pagePaddingH)
RoundedCornerShape(Dimens.cardRadius)
style = Caption                   // 不要再写 TextStyle(fontSize = 10.sp)
```
