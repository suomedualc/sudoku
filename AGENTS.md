# 数独（手写纸 · 简约油墨）· 工作区硬约束

> 本文件只放**每次动手都必须遵守**的硬约束；完整论证、选型依据、模块说明、历史踩坑库在
> `.codebuddy/rules/sudoku-dev.md`（开发纪律，40,792 B）与 `.codebuddy/rules/sudoku-review.md`（审查规则，24,003 B）——**按需读取，不要凭记忆**。
> 与代码冲突时以本文件 §1 红线为准；要违反红线必须先说明理由并取得同意。
> 读文件纪律、上下文压缩止损、删除取证流程等跨工作区规则在 `~/.dsh/AGENTS.md`，此处不重复。

**读法**：接手先看 §2（做什么）→ §4（代码在哪）→ §6（现有行为语义）；写代码过 §1 + §5 + §11；
报结论前过 **§12（怎么"证"而不是"猜"）**，这一节最容易被忽略、也最容易产出假结论。

---

## 1. 红线（22 条，违反前必须说明并获得同意）

### 分层与状态
1. `core/` 只许 Kotlin 标准库 + `kotlin.random.Random`：禁 IO / 时钟 / UI / 平台 API。
2. `state/` 禁 Compose / 平台 API；`GameReducer` 必须纯函数（同 state+action+rng ⇒ 同结果）。
3. 改 `core/` 同步补 `commonTest`；改 `state/` 行为补 `GameReducerTest` / `GameStoreTest`（固定种子）。
4. 改 `state/` 对外类型 / Action / `Screen` 同步更新 `docs/01` §5。
5. 新增依赖或升版本先在 `gradle/libs.versions.toml` 对齐并登记理由（Kotlin 与 CMP 同一版本目录锁定）。

### 视觉（手写纸 · 简约油墨）
6. **只有纸与墨**：不得引入色相（红/绿/蓝语义色，含第三方库彩色）；层次只用墨浓淡（`Ink.Alpha.*`）、线重（`DesignTokens.Stroke.*`）、线型。
7. 颜色与字体只在 `ui/theme/Ink.kt`，尺寸只在 `ui/theme/DesignTokens.kt`；组件里禁裸色值与魔法尺寸。
8. 可见控件一律走自绘墨线组件（`InkSurface` 家族）；引入第三方外观型组件（`Button/Card/Switch/TopAppBar`）属回退，需先说明理由。
9. **覆盖层一律 `TopDrawer`**；禁 `Dialog` / `Popup` / 自造遮罩。页面根节点必须**先** `drawer.handleKey(event, drawerKeys)` 再处理自己的键（预览事件父先于子，不仲裁会双层同时响应）。
10. 手绘线条必须用 `inkLine / inkRoundRect / inkHatch`，抖动种子由内容派生（纯函数）；禁 `Random()` 抖动源。
11. 绘制尺寸一律 dp / `toSp()`；禁把 `DrawScope` 像素值当 dp/sp 用。

### 工程
12. 一次性提示走 `Reduction.message` → `GameViewModel.messages` → Snackbar；不得塞回 `GameState`，不得新增弹窗反馈。
13. 计时只走"注入的单调时钟算秒 + `SyncElapsed`"；禁在 reducer 里读时钟或累加 Tick。
14. `archive/` 的 TS / Rust 旧实现只作参考，不得当当前实现引用或复制。
15. 文档与代码必须同一次改动内保持一致。
16. 打包标识必须纯 ASCII 无空格（`packageName` / `vendor`）；改 `nativeDistributions` 后必须真跑 `packageMsi`，只看 `createDistributable` 通过不算。
17. 应用图标是生成资产：只经 `tools/make-icon.ps1` 产出，不手改二进制、不在构建期临时生成；构图必须九宫格 + 数字（禁落子/棋子形态）。
18. 视觉常量必须来自令牌：字号只取 `Ink.Type` 六档；纸只走 `Ink.Paper/PaperShade/PaperSheet`、墨只走 `Black/Grey/Light/Faint`；线宽只走 `DesignTokens.Stroke`；动效只走 `DesignTokens.Motion`（退场快于进场、开局显影 ≤ 200ms）。新增档位须先加令牌并写用途。
19. 可读性硬线：`Light` 及以上对三种纸底 ≥ 4.5:1（WCAG AA）；`Faint` 只用于禁用态；改色值前先跑对比度量算（算法见 `docs/07` §5）。候选数字 `Alpha.Hint` 是唯一允许低于 AA 的信息色（冗余通道）。
20. 无障碍不能回退：棋盘必须**两级语义**（整盘汇总 + 81 格子节点），格子文案由 `cellA11yLabel` 纯函数产出（改文案同步 `BoardSemanticsTest`）；格子不进 Tab 序；语义层不许吞点击。
21. 新增动画必须过 reduced-motion：时长一律经 `motionDurationMs(baseMs)`（开启时返回 0，不做半速）；平台判定只在 `prefersReducedMotion()` 里做。
22. 字体是分发资产：正文打包霞鹜文楷（`Ink.FontText`）、数字打包 Nunito（`Ink.FontDigits`），OFL 1.1 原文随包；**数字字体只用于数字读数**，且**必须保留霞鹜文楷回退**（`digitFontChain()` 长度为 2，否则"已暂停"渲染成豆腐块）；打包图标与运行时图标必须同源（`main.kt` 显式设 `window.iconImages`）。

---

## 2. 项目状态（接手先看，决定"做什么"）

| 项 | 现状 |
|---|---|
| 产品形态 | **纯单机数独**，无对弈 / 联网 / 社交 / 账号 / 内购。刻意不做（见 `docs/05` §6） |
| 版本 | `versionName 2.1.0` / `versionCode 20100`；**versionCode 由 versionName 推导**（`major*10000 + minor*100 + patch`），改 name 必须同改 code |
| 平台 | Desktop(JVM) ✅ 已交付 MSI | Android ✅ 已启用（APK 可构建、三档模拟器验证） | iOS / macOS / Web：**接入即用但未接**（缺 target 与 entrypoint，`core`/`state` 零平台依赖所以业务代码不用动） |
| 桌面产物 | `createDistributable` → `binaries/main/app/SudokuInk/`（自带 JRE）；`packageMsi` → `SudokuInk-2.1.0.msi`（首次需联网下 WiX ≈99 MB） |
| 仓库 | 远端 `sudoku` = `github.com/suomedualc/sudoku`；分支 `main` |
| Android SDK | `compileSdk 36` / `minSdk 26` / `targetSdk 36`（minSdk 26 的硬原因：`ATOMIC_MOVE` 自 API 26 起） |

### 发布阻塞（只有这三项，不是缺陷）

| 项 | 性质 | 阻塞原因 |
|---|---|---|
| **A3 AAB 签名** | 前置 | 无签名配置；需密钥保管 / 轮换的产品决策，**不要自行生成密钥** |
| **A5 真机矩阵** | 前置 | 所有 Android 结论均来自模拟器（软渲染）；需 ≥1 台实体设备覆盖 8.0/10/12/14 |
| **横屏真机验证** | 前置 | 同上 |

> 其它"未完成"如无 CI、无 iOS/Web、无 instrumentation 测试，都是**已决定不做或已接受**，不要当缺陷报（见 §7 白名单）。

### 能力边界（别白费力气）

- 本机**无实体 Android 设备**：A5、真机触感、真机横屏观感都做不到，只能报"需要设备"。
- 本机**无 macOS**：iOS / macOS 目标无法构建验证。
- 冒烟脚本依赖**前台窗口 + 固定坐标**：无法在锁屏或无桌面交互的环境跑通鼠标类流。

---

## 3. 开发思路（8 条，写代码时的取舍标准）

| # | 原则 | 在本仓的体现 |
|---|---|---|
| 1 | 状态是唯一真相 | 界面全由 `GameState` 派生；Composable 里不 `remember` 业务副本 |
| 2 | 单向数据流（Elm 式） | `输入 → GameAction → reduce → GameState → 重组` |
| 3 | 纯函数优先 | reducer 不读时钟、不取随机数（rng 注入）；固定种子 ⇒ 同一盘面 |
| 4 | 规则与表现分离 | 规则只在 `core/`；`BoardCanvas` 只把状态画成图 |
| 5 | 单一出口 | 颜色字体走 `Ink`、尺寸走 `DesignTokens` |
| 6 | 副作用外提 | 提示→事件通道；计时→UI 心跳；存档→`GameStore` 端口；随机→注入 |
| 7 | 视觉可复现 | 抖动用纯函数种子；文本测量逐帧缓存 |
| 8 | 小步可验证 | 每次改动都能"编译 + 测试 + 四档窗口验收" |

> 已知短板（不是目标）：尚无统一副作用日志 / 回放。

---

## 4. 分层与关键文件

| 层 / 源集 | 约束 | 关键文件 |
|---|---|---|
| `core/` | 零平台依赖 | `Sudoku.kt`（位掩码求解 / 出题）、`CellCursor.kt`（光标纯逻辑）、`KeyMap` 令牌 |
| `state/` | 禁 Compose；纯 reducer | `GameState` / `GameReducer` / `GameStore`(`SaveCodec` v2) / `KeyMap` / `GameStats` |
| `ui/` | 唯一 Compose 层 | `theme/Ink.kt`(配色字体唯一出口) · `theme/DesignTokens.kt`(尺寸唯一出口) · `components/`(墨线控件 / `BoardCanvas` / `TopDrawer`) · `i18n/Strings` |
| `ui/screens` | 对局页**已拆 7 文件** | `GameScreen`(编排) / `GameLayout`(断点纯函数) / `GameTopBar` / `GameBoardStage` / `GameFunctionArea` / `GameKeyHandling` / `GameWinDrawer` |
| `jvmMain` / `androidMain` | 仅装配 | `main.kt`(1180×900，最小 940×720) / `FileGameStore` / `AndroidGameStore`(原子写) |
| 平台接缝 | `expect/actual` | `SafeArea` / `Motion` / `Strings` / `InkFonts` / `SystemBackHandler` / `GameStore`——**新增平台必须补 actual，缺了编译失败** |

**核心不变量**
- `Board = IntArray`(81 格，0–9)；`data class` 的 `equals` 对数组是**引用比较**，禁 `==` 做业务判断；改状态必须 `copyOf()`。
- `legalMask` **包含该格自身值**（"再按同数字 = 清除"依赖它）。
- `targetBlanks`(40/46/52/56) 是**上限**非保证值；`generate` 重试至达标。
- `SaveCodec.decode` 解析失败一律返回 `null`（不抛）；接受 `v=1..2`。
- 统计只存累加值，派生值（胜率/平均）不落盘。

---

## 5. 常见改动：必须同步的地方（漏一处即为缺陷）

| 改动 | 同步点 |
|---|---|
| **加一个偏好开关** | ①`GameState` 字段 ②`GameSettings` ③`SaveCodec.encode` ④`SaveCodec.decode` ⑤`SaveFile.toState()` ⑥`NewGame` 显式继承 ⑦`GameStoreTest.settingsSurviveRestartAndNewGame`。当前七项：strictMode / showNotes / noteMode / hintCandidates / darkMode / keyMap / language |
| 加玩法开关 | 另加 `GameAction.Toggle*` + reducer 分支 + 功能区开关行 + 若影响规则则加测试 |
| 改配色 | 只改 `Ink`；**必须同时给浅/深两套**并重算对比度；跑 `InkThemeTest` |
| 加图标 | ①`InkIcon` 枚举 ②`drawInkIcon` 用 `inkLine` 自绘（禁图标库/图标字体）③`InkIconButton` 承载，说明走 `contentDescription` ④贴窗口边缘必须传 `tooltipAlignment` |
| 改键盘 / 光标 | 光标判据只写 `core/CellCursor.kt`；键位在 `state/KeyMap.kt`；**"按键→令牌"换算只在 `ui/components/KeyToken`**（state 层禁 Compose）；跑 `smoke-e2e.ps1 -Flow keys,keymap` |
| 改棋盘语义 | 只能 `weight(1f)` 等分，**禁 `fillMax*(1f/9f)`**；验证必须点**最后一格** |
| 加难度 / 屏幕 / 平台 | 见 `.codebuddy/rules/sudoku-dev.md` §3.6 |

---

## 6. 玩法与交互规则（改行为前必须知道"现在的语义是什么"）

| 行为 | 规则（现行，有测试守着） |
|---|---|
| 落子 | 给定格拒绝；笔记模式只作用于空格；**同数字再按 = 清除**；**无变化的输入不写历史** |
| 严格模式 | 目标数字与同行列宫冲突时**拒绝**落子并提示（其它模式允许填、只标冲突） |
| 冲突 | 落子后重算 `conflictFlags` → 棋盘画斜排线 + Snackbar 文案，并**清理同行列宫候选** |
| 判胜 | `isSolved(current) && current == solution`（唯一解题目下等价，双保险） |
| 撤销/重做 | 改盘前压栈（盘面 + 笔记 + `hintUsed` + `hintsCount` + `revealed`），上限 300；`redo` 后重推 `settled`。`elapsed` / `outcomeCounted` **刻意不进快照**（时间不倒流；防"撤销再赢重复计胜"） |
| 提示 | 优先当前选中空格，否则第一个空格；标记 `hintUsed` 并累加 `hintsCount` |
| 光标 | 方向键**逐格**（可停已填格，行/列内环绕，跳过给定格）；Shift+方向 = 跳**下一空格**，该行无空格必须退到可编辑格；**填字后跳转九宫格优先**，宫满退回读序；擦除/笔记/取消同数字**不跳** |
| 计时 | 单调时钟算秒 + `SyncElapsed`；仅 Game 屏、未暂停、未结算时接受；暂停/离开/最小化都停表 |
| 新局 | `NewGame` 重置对局字段，但**继承全部七项偏好** |

**刻意不做**（被问到也别加）：落子音效、联网、每日题、账号、内购、社交、第二套 UI 组件库。

---

## 7. 设计取舍白名单（这些是**决定**，不是缺陷）

审阅 / 重构时**不要**把下列项当问题报，也不要"顺手修好"：

| 项 | 为什么这样 |
|---|---|
| `Ink.setDark` 在组合期写入 | 有守卫 + 同帧防闪烁 |
| `redo` 目标栈截断 | 防御性，实际上限不可破 |
| 严格模式拒绝冲突笔记 | 设计内语义 |
| `elapsed` / `outcomeCounted` 不进 undo 快照 | 见 §6 |
| 统计只存累加值 | 改口径不必迁移旧存档 |
| 句子语料保持原文 | 它是**内容**不是界面文案（i18n 例外） |
| 语言选项各写自己的语言名 | 通用做法（i18n 例外） |
| `Alpha.Hint` 低于 AA | 冗余通道（浮层与键盘都能拿到同一信息） |
| 候选数字画在空格内 | 与玩家笔记靠墨色深浅区分，不靠颜色 |
| 无 CI / 无 iOS·Web / 无 instrumentation 测试 | 已决定不做 |
| `MenuScreen` 焦点与游标模型并存 | 超出"不影响核心功能"边界，**待拍板**，不要擅自改 |

**重构时勿丢的亮点**：`SaveCodec` 逐字段宽松降级（坏难度→Normal、缺行→默认、整体失败→null）；`tmp + ATOMIC_MOVE` 原子写且同目录；双栈 `takeLast(300)`；`stripPeerNotes` 填数后清同行列宫笔记；`CellCursor.nextAfterFill` 的"宫优先 + 回退读序"（KDoc 有论证）；`Ink.Alpha.TypeBox` 两套主题各取一档且附对比度实测；`I18nGuardTest` 用 JDK 反射零依赖守键一致性。

---

## 8. 验证（每次提交前）

```powershell
.\gradlew.bat :composeApp:jvmTest --offline           # 161 项（commonTest 126 + jvmTest 35）
.\gradlew.bat :composeApp:testDebugUnitTest --offline # 133 项（= 同一批 126 重复 + Android 独有 7）
# 去重合计 168 项 —— 两个命令重复跑 commonTest，不要把 161+133 相加
powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke-e2e.ps1 -Flow <名字>
# 11 条流：menu / exit / win / theme / bar / ux / keys / keymap / stats / lang / reveal
.\gradlew.bat :composeApp:createDistributable          # 改运行期行为后重建便携版
.\gradlew.bat :composeApp:packageMsi                   # 仅在动过 nativeDistributions 时必须真跑
```

| 改了什么 | 必须额外跑 |
|---|---|
| `core/` / `state/` | 补用例；`GameReducerTest` / `GameStoreTest` / `CellCursorTest` |
| 配色 | `InkThemeTest`（两套主题前三级 ≥ 4.5:1） |
| 棋盘绘制 / 语义层 | `BoardCanvasUiTest`（81 节点 + 点击穿透 + 暂停遮读屏） |
| 字体 / 资源 | `BundledFontsTest` / `BundledAssetsParityTest`（SHA-256 逐字节） |
| 覆盖层 / 键盘映射 | `smoke-e2e.ps1` 并**看截图** |

- 四档窗口肉眼验收：**1180×900 / 800×600 / 500×1000 / 1024×768**。
- 键盘路径：首页 ↑↓ + Enter；对局内方向键 / 数字 / Ctrl+Z/Y；`Esc` 收浮层；通关后 Enter/Esc。
- 鼠标路径：点空格弹悬浮面板 → 点键填数 → 面板消失。

---

## 9. 环境硬事实（本项目）

| 项 | 值 |
|---|---|
| `JAVA_HOME` | **JDK 21**（17–24 可用；指向 JDK 25 直接失败，报错只有一串版本号） |
| 依赖 | Kotlin 2.4.0 + CMP 1.11.1 + AGP 8.10.1 + Gradle 8.14.4；已缓存，日常加 `--offline` |
| 存档 | `~/.sudoku-ink/save.txt`（纯文本，可删以清空续局）；字体缓存在 `~/.sudoku-ink/fonts/` |
| 可再生产物 | `build/`、`composeApp/build/` 可随时清理；`packageMsi` 需联网下 WiX（约 99 MB） |
| 冒烟脚本 | 置顶 + 固定坐标 + 每次都断言前台是应用；**换机器先排除 DPI 因素**（HiDPI 150% 下固定坐标会落空，形似 UI bug）；脚本保持 ASCII-only |

---

## 10. 审查（CR）纪律

| 级别 | 定义 | 处置 |
|---|---|---|
| **P0** | 功能不可用 / 数据丢失 / 崩溃 / 红线被破坏 | 必须修，阻塞合入 |
| **P1** | 功能性 bug / 红线违规且用户可感知 | 本轮修或立即排期 |
| **P2** | 死代码 / 过时注释 / **文档漂移** / 防御性校验缺失 | 择机修，允许挂账 |

1. **证据先行**：评论前读完整文件，不只读 diff 片段；依赖生命周期的问题追到调用方。
2. 结论必须带 `文件:行`；禁纯表扬、禁"可能/也许"式评论。
3. **i18n 红线是本项目最高频违规源**：界面上任何可见文字 / 读屏文案 / 悬停提示一律走 `ui/i18n/Strings` 中英双份，reducer 只发 `Msg`。审查动作：对 commonMain 做**中文字面量扫描**（排除注释 / i18n 目录 / 句子语料 / `AppLanguage.label`）。
4. 每个发现先对照 `.codebuddy/rules/sudoku-review.md` §4 模式库：回归（升级严重度）/ 新问题 / **已接受取舍（不报）**。
5. ≥3 个发现时派 2 个子代理并行独立复核（存在性 / 行号 / 严重度 / 误报），2/2 采信、1/2 带注采信、0/2 剔除。
6. 修完必须跑 **§8 验证命令**，并把结论回写 review 文件 §5 开放问题清单（保持其为最新真相）。

**已判定不成立，不要再报**：出题卡 UI（实测 0–1ms）；严格模式拒绝冲突笔记（设计内）；`redo` 截断撤销栈（防御性）；组合期写 `Ink.setDark`（守卫 + 防闪烁）；**无 CI**（已按用户决定删除，不要擅自重建）。

---

## 11. 高频踩坑（设计取舍与历史坑）

| # | 坑 | 判据 |
|---|---|---|
| 1 | 数组 `equals` 是引用比较 | `GameState` / `Game` / `notes` 用 `==` 做业务判断 = P0 |
| 2 | `Snapshot` 漏字段 | 出现"`hintUsed` 回退而 `hintsCount` 不回退"式矛盾 |
| 3 | `aspectRatio` 高度优先 | Column 非 weight 子项拿"剩余"高度 → 棋盘吃满、键盘 0 高 |
| 4 | `DrawScope.size` 是像素 | `(px).sp` 再乘 density，高 DPI 数字溢出 = P0 |
| 5 | 抖动源不可确定 | `Random()` 抖动 → 每次重绘线条都变（界面"抖"）= P0 |
| 6 | 语义网格等分 | `fillMax*(1f/9f)` 逐格缩水 → 读屏与画面错位约 65%；必须 `weight(1f)` |
| 7 | `pointerInput` 协程里读 `size.height` | 首次布局前是 0 且只在 key 变化时重启 → 悬停/换边"永远不动"；用 `onSizeChanged` 记账 |
| 8 | 悬停只认 `Move` | 指针刚进入是 `Enter` → 漏"一步跨进棋盘"；用 `change.pressed == false` |
| 9 | 覆盖层纸片不吃点击 | 点纸上空白穿透到遮罩 → 抽屉误关；纸片要 `detectTapGestures {}` |
| 10 | 存档非原子写 | 直接 `writeText` 覆盖 = P0；必须同目录临时文件 + `ATOMIC_MOVE` |
| 11 | 存档实例作用域 | 在会被重建的作用域（Activity `remember`）建 `MutexGameStore` → `OverlappingFileLockException` 静默只读 = P0；必须进程级单例 |
| 12 | `LaunchedEffect(Unit)` 陈旧捕获 | 循环内引用会变的局部值 → 语言切换后仍用旧语言；用 `rememberUpdatedState` 或循环内现取 |
| 13 | 图标只嵌进 exe | jpackage 只改文件图标，窗口/任务栏仍是 Java 图标；须 `main.kt` 设 `window.iconImages`，读资源用 `Class.getResourceAsStream("/x.png")`（ClassLoader 版不接受前导 `/`，静默 null） |
| 14 | 文档漂移（M8） | 数字与实现不符（流数 / 入口数 / 设置项数 / 测试数）——**本项目反复发生，CR 必查** |
| 15 | 光标"回不去" | 方向键必须能停在**已填格**（"回去改一格"不许藏在修饰键后）；自动跳转落点必须是**空格** |
| 16 | 常驻标识走形状 | 亮度通道已被临时高亮占满；常驻标识改用线（字框），否则两层信息糊在一起 |

---

## 12. 工程方法：怎么"证"而不是"猜"（本项目 76 次提交总结）

> 这一节是**元经验**：它决定你报出的结论可信度。违反它产生的不是代码 bug，是**假结论**。

### 12.1 核实事实的三条铁律

| # | 规则 | 本仓实证 |
|---|---|---|
| 1 | **`Test-Path` 不能证明文件名大小写** | `Test-Path 'agents.md'` / `'AGENTS.MD'` 全返回 `True`（NTFS 不区分大小写）。`-LiteralPath` 只关通配符，**不关大小写**。要证明落盘名，必须**目录枚举后比对真实文件名**：`[IO.Directory]::GetFiles(dir,'*.md')` 或 `Get-ChildItem \| Where Name -cmatch '^AGENTS\.md$'` |
| 2 | **不要拿"命令跑通"当"行为正确"** | `gradlew jvmTest` 返回 `UP-TO-DATE` 时**根本没跑测试**；必须看 `test-results/*.xml` 里的实际 `tests` 数，或加 `--rerun-tasks` |
| 3 | **截图 / 渲染帧不等于 UI 正确** | 本仓实测：`smoke_3_game_hard` 首跑是**空白棋盘**，复跑正常——那是截在显影动画首帧（`RevealMs=160`），不是渲染缺陷。**判断前先排除"截图时机"**；每张图先看像素尺寸（237×39 是残窗，不是应用帧） |

### 12.2 危险命令清单（会静默出错）

| 陷阱 | 后果 | 正确做法 |
|---|---|---|
| `Sort-Object -Unique` | **大小写不敏感**：`…key.key` 被当作 `…Key.Key` 的重复**删掉**（本仓实测，导致 10 处编译错误） | 用 `[SortedSet[string]]::new([StringComparer]::Ordinal)` |
| `$a, $b = @(1,2)` | 按元素分配，`$a=1,$b=2`；想解构数组会报"空数组索引" | `$pair=@(1,2); $a=[int]$pair[0]` |
| `-replace $pat, "internal $1 $n"` | 三表达式报错，须整体求值 | `-replace $pat, "internal `$1 $n"` |
| heredoc `<<'EOF'` | PowerShell **不支持**，`git commit -F -` 会解析失败 | 写临时文件 `git commit -F file`，用完删 |
| `Get-Content -Raw \| .Replace() \| Set-Content` | 破坏编码 / BOM，且内容在历史里多存一份 | 用 `edit` / `write` 工具；必须脚本改时用 `UTF8Encoding($false)` |
| `Out-File` 后接管道 | 无控制台输出时看不到结果，误判"脚本没跑" | 显式 `Write-Output` 摘要 |
| `pwsh` 控制台 | GBK 编码，`print` 中文即崩 | 写 UTF-8 文件再用 `read` |

### 12.3 大文件拆分的可行流程（本仓已成功一次）

> 实例：`GameScreen.kt` 1,296 → 664 行 + 6 个兄弟文件，**886 行代码逐行等价**，161 项测试零回归。

| 步骤 | 要点 |
|---|---|
| ① 定边界 | 用**括号配对 + 下一个顶层声明起点**自动算函数体起止，**不要靠行号估算**（`Get-Content` 与 `read` 行的切分不一致，我因此连续错 3 次） |
| ② 判可见性 | 待搬的 composable 全是 `private` → 跨文件必须提为 **`internal`**，这是唯一允许的语义改动 |
| ③ 算 import | 按"去注释后的代码里是否出现**未限定**符号"反推；**别手抄** |
| ④ 验等价 | 拆分前后**规范化比对**：去空行 / 去注释 / 去 import / 去 package → 排序后 `Compare-Object -CaseSensitive`，期望**零差异**（可见性改动除外） |
| ⑤ 验证 | `compileKotlinJvm` + `compileDebugKotlinAndroid` + 双测试套 + **实机冒烟** |
| ⑥ 何时该委派 | 只有"**多文件独立审计**"适合并行子代理。单文件拆分是**单冲突点**，委派只会争同一文件、且任一处错都让测试整体变红 → **不要委派** |

### 12.4 跨工作区复核（结论要能被别人独立复现）

- 复核请求写进**对方工作区的磁盘文件**（如 `X:\dsh-auto\_handover\work\…md`），并附**对方要跑的命令**，不要只发结论。
- 复核方**必须重新取证**，不得采信请求单里的数字；请求单自身的方法缺陷也应被指出。
- 报告格式：`检查点 → 实际值 → 判定` 三列表，外加**你实际执行的命令**。

**写复核请求时本身会犯的 4 个错**（都被实际复核方抓出来过，直接导致假结论或漏错）：

| # | 错误 | 后果 | 正确做法 |
|---|---|---|---|
| 1 | **只给期望值不给算法** | "去重 168" 若按字面 `Sort-Object -Unique` 会得 **294**（jvmTest 的 testcase name 带 `[jvm]` 后缀）→ 误判"断言不成立" | 写明"**先剥 `[平台]` 后缀再 union distinct**"；任何合成指标都要给可复现的口径 |
| 2 | **假设替换等长** | `Gradle 8.12`→`8.14.4` 是 **+2 字符**，导致抄了旧字节数（40,790 应为 40,792）写进 AGENTS.md | 先算 diff 再定期望字节数；只写"改了几处"不够 |
| 3 | **正则里误用 `\|`** | `Select-String -Pattern 'versionCode\|versionName'` 匹配的是**字面竖线**，什么都匹配不到 → 假阳性"缺字段" | 或关系直接写 `'versionCode\|versionName'` 的反面：`'versionCode|versionName'` |
| 4 | **只查被引用的文件，不查引用本身** | 复核了 `sudoku-dev.md` 却漏了 AGENTS.md 里对它的**字节数引用**，恰好放过自己的错 | 断言与被引用的副本**都要查**；列表里显式写"引用一致性" |

**另两条已踩过的坑**：
- `[xml]` 解析测试报告时，`testsuite` 根节点的 `name` 属性会**遮蔽** `DocumentElement.Name`，按 `.testsuite.tests` 取会得 0 —— 用正则取 `<testsuite … tests="N">` 更稳。
- `docs/08` 这类**历史报告**里的旧版本号属"当时快照"，**不要改**，除非明确要求回填历史。

### 12.5 本项目的实际验证成本（估算用）

| 动作 | 耗时 | 备注 |
|---|---|---|
| `jvmTest` + `testDebugUnitTest` | 15–60 s（冷）/ 1–5 s（增量） | 294 个用例总数（含重复 126） |
| `compileKotlinJvm` + `compileDebugKotlinAndroid` | 4–15 s | 改 commonMain 后必跑双端 |
| `createDistributable` | 3–10 s | 改运行期行为后必须重建，否则冒烟测的是旧 exe |
| 单条冒烟流 | 30–60 s | 每条流会启动 / 关闭应用 1–3 次；**逐流单跑比 `-Flow all` 稳** |
| `packageMsi` | 首次 +联网下 WiX | 仅在动 `nativeDistributions` 时跑 |

> **冒烟可靠性经验**：`-Flow all` 一次连跑多流时，`Pin`（`SetWindowPos`）会抢走前台，导致后续流的按键落到别的窗口（`FOREGROUND LOST`）或窗口尺寸残留。**稳定做法是每条流单独起一个进程**，并在流之间 `Start-Sleep`。
> 窗口尺寸自检：DPI 150% 下 `Pin` 后应为 **1770×1350**；若得到 1244×… 之类，说明 `Pin` 没生效，**此时截图不可信**，不要据此报 UI 缺陷。

---

## 13. 源码位置索引

- 详细规则：`.codebuddy/rules/sudoku-dev.md`（开发，40,792 B）· `.codebuddy/rules/sudoku-review.md`（审查，24,003 B）
- 文档：`docs/01` 架构 · `docs/02` 设计规范 · `docs/03` 开发流程 · `docs/04` AI 协作 · `docs/05` 发展规划 · `docs/06` 图标 · `docs/07` 设计评审 · `docs/10` 优化方案 · `docs/11` Android 上线 · `docs/13` CR 报告 · **`docs/14` 交接文档（接手先读）**
- 工具：`tools/make-icon.ps1`（图标唯一来源）· `tools/get-fonts.ps1`（字体钉死 URL/版本）· `tools/smoke-e2e.ps1`（实机冒烟）
