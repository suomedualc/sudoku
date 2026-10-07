# 数独（手写纸 · 简约油墨）开发纪律 · v2.3

> 本文件是"系统层"上下文（自动加载），既是**强制规则**，也是本工程的**开发思路 / 选型依据 / 模块说明 / 迭代计划**的简版。
> 详细论证见 `docs/01-架构设计.md`、`docs/02-设计规范.md`、`docs/03-开发流程.md`、`docs/05-发展规划.md`。
> 读法：**红线在 §0（每次改代码都要过一遍）**；不确定"为什么这样设计"看 §1–§2；动手改具体模块看 §3.6；排期看 §4。
> 与代码冲突时以本文为准；要违反红线必须先说明理由并取得同意。

---

## 0. 红线（违反前必须说明并获得同意）

**分层与状态**

1. `core/` 只允许 Kotlin 标准库与 `kotlin.random.Random`：**禁止** IO / 时钟 / UI / 平台 API。
2. `state/` **禁止** Compose / 平台 API；`GameReducer` 必须是纯函数（同一 state + action + rng ⇒ 同一结果）。
3. 改 `core/` 算法必须同步补 `commonTest` 用例；改 `state/` 行为必须补 `GameReducerTest` / `GameStoreTest` 用例（固定种子）。
4. 改 `state` 对外类型 / Action / `Screen` 必须同步更新 `docs/01-架构设计.md` §5。
5. 新增依赖或升级版本，必须先在 `gradle/libs.versions.toml` 对齐并登记理由（Kotlin / CMP / miuix 三者强绑定）。

**视觉（手写纸 · 简约油墨）**

6. **只有纸与墨**：任何新组件都不得引入色相（不得出现红/绿/蓝语义色，也不得使用 miuix 的彩色语义色）；
   层次只能用墨的浓淡（`Ink.Alpha.*`）、线重（`DesignTokens.Stroke.*`）、线型（实线 / 虚线 / 排线）表达。
7. **颜色与字体只在 `ui/theme/Ink.kt`，尺寸只在 `ui/theme/DesignTokens.kt`**；组件里禁止裸色值与魔法尺寸。
8. **所有可见控件走自绘墨线组件**（`InkSurface` 家族：`InkButton` / `InkKey` / `InkToggleRow` / `InkPanel`）；
   引入 miuix 的 `Button / Card / Switch / TopAppBar` 等**外观型组件**属于回退，需先说明理由。
9. **覆盖层一律用 `TopDrawer`**（`ui/components/TopDrawer.kt` + `TopDrawerController`）：难度选择 / 退出确认 / 通关结算
   以及今后任何"请先做决定"的浮层都用它；**禁止** `Dialog` / `Popup` / 自造遮罩 / 第二套覆盖层实现。
   页面根节点必须在 `onPreviewKeyEvent` 里先 `drawer.handleKey(event, drawerKeys)` 再处理自己的键——
   Compose 的预览事件是**父先于子**，不仲裁就会出现"抽屉打开时底下的菜单/棋盘同时响应"。
9. 手绘线条必须使用 `inkLine / inkRoundRect / inkHatch`，且抖动的种子必须由内容派生（纯函数）——
   **禁止** `Random()` 之类每次重绘都会变的抖动源（会让界面"抖"）。
10. 绘制尺寸一律用 dp / `toSp()`，**禁止**把 `DrawScope` 的像素值直接当 dp / sp 用。

**工程**

11. 一次性提示统一走 `reduce` 返回的 `Reduction.message` → `GameViewModel.messages` 事件通道 → Snackbar；
    **不要**把事件塞回 `GameState`，也不要新增弹窗式反馈。
12. 计时只允许"由注入的单调时钟算出秒数 + `SyncElapsed`"这一条路径；禁止在 reducer 里读时钟或累加 Tick。
13. `archive/` 里的 TS / Rust 旧实现只作参考，**不得**当作当前实现引用或复制。
14. 文档与代码必须同一次改动内保持一致。
15. **打包标识必须是纯 ASCII、无空格**（`packageName` / `vendor`）；改 `nativeDistributions` 后必须真跑一次
    `packageMsi` 验证，不能只看 `createDistributable` 通过就宣布打包没问题。
16. 应用图标是**生成资产**：只通过 `tools/make-icon.ps1` 产出（运行时 `composeApp/icons/` + 交付 `assets/icon/`），
    不手改二进制、不在构建期临时生成。构图必须是**九宫格 + 数字**（禁止落子/棋子形态，避免与五子棋混淆），
    规范见 `docs/06-应用图标设计.md`。
17. **视觉常量必须来自令牌，不许在调用点写裸值**（铁律见 `docs/02` §9）：
    字号只能取 `Ink.Type` 六档（`Meta/Caption/Body/Title/Headline/Display`，含字距与行高）；
    颜色/透明度的纸只走 `Ink.Paper/PaperShade/PaperSheet`、墨只走 `Black/Grey/Light/Faint`；
    线宽只走 `DesignTokens.Stroke`；动效时长只走 `DesignTokens.Motion`（退场快于进场）。
    新增档位必须先加令牌并写清用途——"看起来差不多"不是加档的理由。
18. **可读性硬线**：`Light` 及以上对三种纸底都要 ≥ 4.5:1（WCAG AA 小字），`Faint` 只能用于禁用态文字。
    **改任何色值前先跑对比度量算**（算法与结论见 `docs/07` §5）；候选数字 `Alpha.Hint` 是唯一被允许低于 AA 的
    信息色，理由是它为冗余通道（浮层与键盘都能拿到同一信息）。
19. **无障碍不能回退**：棋盘必须保持**两级语义**（整盘汇总 + 81 个格子节点），格子文案由
    `cellA11yLabel` 纯函数产出（改文案要同步 `BoardSemanticsTest`）；**格子不许进 Tab 序**（键盘模型是方向键选格），
    语义层**不许吞掉点击**（有专项 UI 测试守着）。新增棋盘可视化（如新的提示层）要评估是否需要进朗读文案。
20. **新增动画必须过 reduced-motion**：任何 `animate*AsState` / `tween` 的时长都要经 `motionDurationMs(baseMs)`
    （开启时返回 **0**，不做半速播放）；平台判定只在 `prefersReducedMotion()` 里做，不在调用点读系统设置。
21. **字体是分发资产**：正文用打包霞鹜文楷（`Ink.FontText`）、数字用打包 Nunito（`Ink.FontDigits` / `Ink.digitStyle`），
    都在 `resources/fonts/`（OFL 1.1，授权原文随包）。**数字字体只用于"数字读数"**（棋盘格内 / 数字键盘键位 /
    计时 / 进度 / 通关用时），句子里的数字仍走正文字体；**数字族必须保留霞鹜文楷回退**
    （`digitFontChain()` 长度为 2，否则暂停时计时位的「已暂停」会渲染成豆腐块）。改字体来源 / 版本要同步 `tools/get-fonts.ps1`（钉死 URL 与版本）并保留 OFL 原文；
    若做子集化，属"修改版本"，需按 OFL 第 3 条处理保留字体名（霞鹜文楷 OFL 声明保留「LXGW / 霞鹜」等名）。
    **打包图标与运行时图标必须同源**：jpackage 只把图标嵌进 exe；AWT 窗口默认仍显示 JDK 的 Java 图标，
    因此 `jvmMain/main.kt` 必须显式设置 `window.iconImages`（PNG 经 `jvmMain { resources.srcDir("icons") }` 进 jar）。

**自检**：`.\gradlew.bat :composeApp:jvmTest --offline` 全绿（当前 71 项）+ **实机冒烟**
（`tools/smoke-e2e.ps1`，置顶 + 固定坐标，见 §5）+ **实机试玩**（§6 第 4–5 条）+ 肉眼验收四档窗口。

---

## 1. 开发思路（为什么这样写代码）

| # | 原则 | 在本仓的体现 | 反例（不要这样写） |
|---|---|---|---|
| 1 | **状态是唯一真相**，UI 只是渲染函数 | 所有界面内容都从 `GameState` 派生，UI 不缓存副本 | 在 Composable 里 `remember` 一份"当前数字"再自行修改 |
| 2 | **单向数据流**（Elm 式） | `输入 → GameAction → GameReducer.reduce → GameState → 重组` | 直接从 UI 修改状态字段、或把业务分支写进屏幕 |
| 3 | **纯函数优先**，可回放可测试 | reducer 不读时钟、不取随机数（rng 注入）；固定种子 ⇒ 同一盘面 | 在 reducer 里 `Random.Default` / `System.currentTimeMillis()` |
| 4 | **规则与表现分离** | 规则只在 `core`；`BoardCanvas` 只负责"把状态画成图" | 在 Canvas 里判断"这一步合不合法" |
| 5 | **单一出口**：颜色字体走 `Ink`、尺寸走 `DesignTokens` | 全站只有一个改为墨色/手写体的地方 | 在组件里写 `Color(0xFF...)` / `16.dp` |
| 6 | **副作用外提** | 提示 → Snackbar；计时 → UI 心跳；存档 → `GameStore` 端口；随机源 → 注入 | reducer 里直接弹窗 / 写文件 |
| 7 | **视觉可复现**：手绘感必须确定 | 抖动由 seed 纯函数生成，重绘不闪动；文本测量跨帧缓存 | 用 `Random` 抖动、每次重组重算文本布局 |
| 8 | **小步可验证** | 每次改动都要能"编译 + 测试 + 四档窗口肉眼验收" | 一次性大改，无法定位回归 |

> 当前认知的诚实标注：第 6 条只做到"外提"，尚未做到"可观测的 Effect 模型"——提示仍是 `message` 字段、
> 计时仍由心跳累加（真实时间源与事件通道见 §4 P1）。这是已知短板，不是设计目标。

---

## 2. 技术选型依据（选了什么，为什么不选替代）

| 领域 | 选择 | 备选 | 理由 | 代价 / 风险 | 何时应改选 |
|---|---|---|---|---|---|
| 语言/框架 | Kotlin + Compose Multiplatform | Flutter / Tauri+Web / 原生双写 | 一份 UI 跨端；`core/state` 零平台依赖可 100% 复用 | 生态小于 Flutter；iOS 构建需 macOS 主机 | 若必须覆盖 iOS 且无 Mac，评估 Flutter（会丢掉现有栈） |
| 骨架与提示 | miuix（`MiuixTheme` / `Scaffold` / `SnackbarHost`） | 纯 Compose 自建骨架 | 主题上下文、安全区内边距、Snackbar 生命期与无障碍都已成熟 | miuix 标注 experimental，签名可能变 | 若 miuix 长期不更新或被迫升 compileSdk，可改为自建骨架（`Box` + `WindowInsets` + 自绘提示条） |
| 视觉组件 | **自绘墨线组件**（`InkWidgets.kt`） | miuix `Button/Card/Switch` | 手写纸风格与填充色块 / Material 圆角的观感冲突；自绘才能做到"折线微弯 + 叠墨 + 五态一致" | 需要自己维护交互态（悬停/按压/焦点/禁用）与无障碍语义 | 若将来要"回到 miuix 观感"，只需替换 `InkWidgets` 一层，页面代码不动（组件边界已隔离） |
| 字体 | **打包字体资源**（霞鹜文楷 + Nunito，OFL 1.1） | 只探系统手写体 | 跨设备 / 跨平台字形一致；数字有真实字重可分层（给定 Medium / 填入 Normal） | 仓库 +24.7MB；再分发需随附 OFL；桌面 `Font` 工厂无 classpath 入口，须落临时文件装载 | Android/Web 需按各端资源机制另做装载（必要时子集化，但子集属"修改版本"，需按 OFL 第 3 条改名） |
| 棋盘渲染 | Compose `Canvas` 自绘 | 81 个 Composable / 图片贴图 | 81 格一次绘制远轻于 81 个节点；完全控制墨色分层与手绘线条 | 需自己处理测量、字号、缓存与无障碍 | 要做逐格无障碍节点时，改为"语义网格 + Canvas"混合（§4 P2） |
| 状态管理 | 自写纯 reducer + `GameViewModel` | ViewModel + Flow / MVI 框架 | 规模小、可单测、可回放、零框架依赖 | 需自己补生命周期、事件通道、派生缓存 | 出现多数据源（战绩 + 每日题）与复杂副作用时，引入 Effect 模型 + Repository（`docs/05` 阶段 D） |
| 存档 | 端口 + 注入（`GameStore` / `SaveCodec`） | `expect/actual` / DataStore / SQLDelight | `state` 保持零平台依赖；桌面写单文件、测试用内存实现；编解码纯文本可单测 | 需为每个平台写实现；只存对局不存设置 | 需要结构化查询（战绩统计）时换 SQLDelight / DataStore（换实现即可，不动状态机） |
| 求解 / 出题 | 位掩码 + MRV 回溯 + 贪心挖洞 + 唯一解校验 | 朴素回溯 / 题库资源 | 候选判断 O(1)，出题实测 1–4 ms | 空格数只是上限（大师档实测 22/25 达标） | 做"技巧难度"时新增技巧求解器，出题改为"技巧可解性"驱动 |
| 计时 | UI 心跳 `delay(1000)` 累加 | `TimeSource.Monotonic` 差值 | 实现最简单、无平台依赖 | 有累积漂移；窗口最小化仍走表；不可测 | **应当改**（§4 P1）：真实时间源 + 时钟注入 |
| 提示反馈 | `Reduction(state, message)` → `Channel<String>` → `SnackbarHost` | 自绘墨条 / `SharedFlow` | 复用 miuix 的通知通道，零额外依赖；相同文案也能逐条送达（已修） | 需 ViewModel 持有 Channel 与收集协程 | 若要做"可撤销的提示条"，改自绘墨条（`docs/05` §4） |
| 测试 | `kotlin.test` + 固定种子 | 断言库 / 属性测试 | 零依赖、跨端同一套用例 | 无 UI 自动化 | UI 复杂化后引入 CMP UI 测试（需加依赖并验证可行性） |

---

## 3. 模块设计说明

### 3.1 `core/`（领域内核，零平台依赖）

| 内容 | 说明 / 契约 |
|---|---|
| `typealias Board = IntArray` | 约定：长度 81，取值 0–9（0 = 空格）。**API 目前不做校验**（已知短板，勿传非法数据） |
| `Difficulty` | 4 档 + `label` + `targetBlanks`（40/46/52/56）。注意它是**上限**而非保证值 |
| `Game` | `puzzle` / `current` / `solution` / `difficulty`，三者各自持有独立数组 |
| `Solver` | 行列宫三个 9 位掩码；`pick()` 返回 `Solved / Dead / Choose(pos, mask)`（MRV + 空候选剪枝） |
| `solve` / `countSolutions(board, limit)` | 带回溯早退；判唯一解只需 `limit = 2` |
| `generate(difficulty, rng)` | 随机终盘 → 随机顺序挖洞 → 每挖一格校验唯一解；**rng 必须由调用方注入** |
| `conflictFlags(board)` | 行 / 列 / 宫三组扫描，返回 81 布尔；内部 `counts[10]` 依赖取值 ≤ 9 |
| `isSolved(board)` / `legalMask(board, pos)` | 判胜；候选掩码（**包含该格自身值**，"再按同数字=清除"依赖这一点） |
| `formatDuration(seconds)` | `MM:SS` / `H:MM:SS`，手写拼接避免平台格式化 API |

### 3.2 `state/`（应用层，纯状态机 + 存档端口）

`GameState` 关键字段：`screen`（**只有 Menu / Game**）、`game`、`selected`、`notes[81]`、
`noteMode / strictMode / showNotes / hintCandidates`、`elapsed / paused`、`hintUsed / hintsCount`、
`revealed / settled / won`、`undoStack / redoStack`（快照栈，上限 300）。
派生属性：`interactive`（Game 屏 + 有对局 + 未暂停 + 未结算 + 未通关）、`settings`（四项偏好投影，供存档）。
**纯 UI 状态不进 GameState**：例如"悬浮输入面板此刻锚在哪一格"由 `GameScreen` 的 `remember` 持有。
**一次性提示不在状态里**（第二轮已迁移）：`reduce` 返回 `Reduction(state, message)`。

| 行为 | 规则 |
|---|---|
| 落子 | 给定格拒绝；笔记模式只作用于空格；同数字再按 = 清除；**无变化的输入不写历史** |
| 移动 | 方向键沿方向逐格前进并**跳过给定格**，仅在本行 / 列内环绕 |
| 冲突 | 落子后重算 `conflictFlags`（棋盘排线 + Snackbar 文案），并清理同行列宫候选 |
| 严格模式 | 目标数字与同行列宫冲突时拒绝落子并提示 |
| 判胜 | `isSolved(current) && current == solution`（唯一解题目下等价，双保险） |
| 撤销/重做 | 改盘前压栈（盘面 + 笔记 + `hintUsed` + `hintsCount` + `revealed`）；重做会重新推导 `settled` |
| 提示 | 优先当前选中空格，否则第一个空格；标记 `hintUsed` 并累加 `hintsCount` |
| 候选提示 | 切换 `hintCandidates`（默认关，**不动选中格**）。渲染在 `ui`：**所有空格**画 `legalMask` 的**半透明灰**数字（整盘 `remember(game.current)` 缓存），玩家笔记优先 |
| 提示事件 | 不存在状态里：`reduce` 返回 `Reduction(state, message)`，由 ViewModel 送进事件通道 → Snackbar |
| 计时 | 秒数由**单调时钟**算出（`SyncElapsed(seconds)`）；仅 Game 屏、未暂停、未结算时接受新值；暂停 / 离开界面 / 窗口最小化都会停表 |
| 新局 | `NewGame` 重置对局字段（盘面 / 笔记 / 计时 / 提示 / 结算标记），但**继承三项偏好**（`strictMode / showNotes / noteMode`） |

**存档**（`GameStore.kt`，v2）：文件内容 = `SaveFile(settings, game)`——
**设置常驻**（`GameSettings(strictMode, showNotes, noteMode, hintCandidates)`），对局可选（`SavedGame(game, notes, elapsed)`）。
`SaveCodec` 纯文本编解码，`v=2` 写出四项设置（+ 有对局时写 `game=1` 与盘面块）；
`decode` 接受 `v=1..2`，v1 无设置行则取默认值，**解析失败一律返回 `null`（不抛异常）**，读到旧文件下次落盘自动升级。
`GameViewModel` 启动时 `load()` 恢复盘面 / 笔记 / 计时 / 设置、动作后 `save()`（每 10 秒节流）、
结算后只把对局移出存档（**偏好保留**）；`canResume` 供首页「继续游戏」使用。

已修复（勿回退）：
- 第二轮：提示事件语义（`Reduction` + `Channel`）、`conflicts/progress` 记忆化（`derivedStateOf`）、
  计时漂移与后台走表（单调时钟 + 最小化暂停）、大师档挖不满 56 空（`generate` 重试至达标）。
- 第三轮：设置持久化（`SaveCodec` v2 + v1 兼容）、通关墨框 + 再来一局、首页三入口键盘导航、
  打包标识（ASCII `packageName`）与图标。
- 第四轮：候选提示（空心数字 + 第 4 项设置）、鼠标/触控笔点空格弹**半透明悬浮输入面板**、
  触屏方案定型（手指不弹浮层，走常驻键盘）。
仍未做：`legalMask` 是否下沉到状态层（目前按盘面 `remember`）、逐格无障碍语义、
移动端命中区放大与候选预览条（M2）。

### 3.3 `ui/`（表现层，唯一允许调用 Compose / miuix 的层）

| 文件 | 职责 | 关键约定 |
|---|---|---|
| `theme/Ink.kt` | 纸墨配色、手写体、绘制原语 | **颜色与字体的唯一出口**；`inkLine/inkRoundRect/inkHatch` 抖动用纯函数种子 |
| `theme/InkFonts.jvm.kt`（jvmMain） | 打包字体装载 + 系统兜底 | 资源落临时文件再 `Font(file)`；正文 = 霞鹜文楷 → 系统楷书；数字 = Nunito + **霞鹜文楷回退链**；失败返回 null（调用方回退通用族） |
| `theme/DesignTokens.kt` | 间距 / 圆角 / 断点 / 线宽 | **尺寸的唯一出口**，不含颜色 |
| `components/InkWidgets.kt` | 墨线控件库 | `InkSurface` 统一五态反馈；`InkText` 是唯一文本出口 |
| `components/BoardCanvas.kt` | 棋盘墨线绘制 | 分层：纸面 → 墨洗 → 格线 → 数字/笔记 → 选中框；文本缓存 + `rememberUpdatedState` |
| `components/NumberPad.kt` | 数字键盘 | `legalMask: Int?`：`null` = 不区分；非 null 时**线重**区分可填 / 不可填（不禁用） |
| `components/FloatingPad.kt` | 半透明悬浮输入面板 + `FloatingPadPolicy`（位置 / 尺寸 / 开合纯逻辑） | **只列该格可填数字**（键 48dp、最多 3 列自适应；候选为 0 时给一行提示）；只用 `PointerType` 判定"精确指针"；**不接管焦点**；位置 / 尺寸 / 开合必须是纯函数并有单测 |
| `components/TopDrawer.kt` | 顶部抽屉（**唯一覆盖层形态**）+ `TopDrawerController` + `TopDrawerKeys` | 全屏遮罩 + 顶部滑入纸片（全宽、限宽 560dp、只有下缘圆角 + 抓手段）；遮罩只在展开时拦指针；键盘仲裁是**纯函数**（有单测）；关闭时 `restoreFocus` 交回焦点；页面根节点必须先调 `drawer.handleKey` |
| `screens/MenuScreen.kt` | 首页 | **只有三个入口**（开始 / 继续 / 退出）+ 难度抽屉 + 退出确认抽屉 + 底部草图；`MenuCursor` + `onPreviewKeyEvent` 提供 ↑↓ / Enter / Esc（跳过置灰项，鼠标点击同步高亮）；键先过抽屉仲裁 |
| `screens/GameScreen.kt` | 对局页 | 宽屏双栏 / 窄屏滚动；暂停白纸遮题；通关结算抽屉（`WinDrawer` 复用 `TopDrawer`，不可点遮罩关闭）；悬浮面板的锚点格与统一出口 `dispatch`（除选格 / 换格 / 心跳外任何动作都收面板）；键序 **抽屉 → 悬浮面板 Esc → 棋盘**（`handleKeyEvent` 只管棋盘，通关期由 `winDrawerKeys` 接管） |
| `App.kt` | 装配 | `MiuixTheme(lightColorScheme())` + `Scaffold` + 两页导航 + Snackbar；铺 `Ink.Paper`；无顶部栏 |

### 3.4 entrypoints

| 源集 | 内容 | 状态 |
|---|---|---|
| `jvmMain` | `main.kt`（1180×900 / 最小 940×720）· `platform/FileGameStore.kt`（`~/.sudoku-ink/save.txt`）· `ui/theme/InkFonts.jvm.kt` | ✅ 已启用 |
| `androidMain` | `MainActivity` + `AndroidManifest`（主题已改系统内置） | ⏸ 未参与编译：缺 `androidTarget()` 与 `compileSdk 37`（`docs/05` §3.1） |

### 3.5 七个容易踩的坑

1. `GameState` / `Game` 里是 `IntArray`，`data class` 的 `equals` 对数组是**引用比较**——不要用 `==` 做业务判断。
2. `Snapshot` 必须覆盖"所有会被改的字段"（漏一个就会出现 `hintUsed` 回退而 `hintsCount` 不回退这类矛盾）。
3. `aspectRatio(1f)` 在高度不足时**以高度为准**，而 `Column` 给非 weight 子节点的高度约束是"剩余空间"——
   棋盘若作为第一个子节点且不设上限，会把后面的数字键盘压成 0 高度（已修：棋盘放 weight 区 + 顶部对齐 + 预留提示条）。
4. 手绘抖动若用 `Random()`，每次重绘线条都会变（界面"抖"）；必须用由内容派生的种子。
5. `DrawScope` 的 `size` 是**像素**；`(px).sp` 会再乘 density —— 字号必须 `toSp()`。
6. 做"点空白处关闭浮层"时，**别把 tap 检测挂在包含棋盘的容器上**：父容器收不到已被子节点消费的点击（等于失效），
   而靠 `consume()` 抢事件又会连带禁掉棋盘上的拖动滚动。正确做法是挂在**不含棋盘的容器**（控制栏 / 面板区），
   并让所有动作走同一个出口（`dispatch`）来收浮层；棋盘自己的点击由 `BoardCanvas` 回传。
7. **图标只嵌进 exe 是不够的**：jpackage 改的是**文件**图标，AWT 创建的窗口仍会用 JDK 的 Java 图标
   （标题栏与任务栏都是它）。必须在 `main.kt` 里设置 `window.iconImages`；而且读资源要用
   `Class.getResourceAsStream("/sudoku-32.png")`——`ClassLoader.getResourceAsStream` **不接受前导 `/`**，
   会静默返回 null，于是"设了图标却没生效"。

### 3.6 常见改动指引

| 想做的事 | 步骤 |
|---|---|
| 改配色 / 线宽 | 只改 `Ink`（颜色墨阶）或 `DesignTokens.Stroke`（线宽），不要在组件里写死；改完过一遍四档窗口 |
| 加一个玩法开关 | ① `GameState` 加字段 ② `GameAction` 加 `Toggle*` ③ reducer 处理 ④ `GameScreen.OptionsPanel` 加一行 `InkToggleRow` ⑤ 若影响规则，加测试 ⑥ 若要落盘，同步 `SaveCodec` |
| 加一个难度 | ① `Difficulty` 加值 ② （若要求达标）改 `generate` 的重试策略 ③ `MenuScreen` 难度遮罩自动出现（遍历 entries）④ 测试断言实际空格数 |
| 加一个新屏幕 | ① `Screen` 枚举 ② `App.kt` 的 `when` 分支 ③ 新 Screen 用 `InkPanel` + `InkButton` 组织 ④ 更新 `docs/01` §2 与 `docs/02` §3 |
| 加一个新平台 | 见 `docs/05` §3.1 / §3.2：确认 miuix 有该平台变体 → 加 target → 写 entrypoint → 实现 `GameStore` → 逐条过输入差异与屏幕档位 |
| 换字体 | 改 `tools/get-fonts.ps1`（URL / 版本）+ `resources/fonts/` 里的文件 + `InkFonts.jvm.kt` 的装载路径，不动任何页面代码 |
| 改打包标识 / 图标 | ① 视觉改动重跑 `tools/make-icon.ps1`（生成 `composeApp/icons/`）② 改 `nativeDistributions` 里的 `packageName`（**保持 ASCII**）/ `description` / `vendor` ③ 真跑 `packageMsi` 并核对产品名与图标 |

---

## 4. 后续迭代计划（与 `docs/05` §5 里程碑对齐）

**P0（阻塞跨平台）**
- Android 落地：`sdkmanager "platforms;android-37" "build-tools;37.0.0"` → AGP + `androidTarget()` → `activity-compose` → 图标 → 模拟器验收（含返回键、边到边、`GameStore` 的 Android 实现）。
- 构建环境固化：`JAVA_HOME` 指向 JDK 21（指向 JDK 25 会直接失败）。

**P1（体验与状态机收口，对应 `docs/05` M1 ✅ 已达成）**
- ✅ 事件通道：`message` → `Reduction(state, message)` + `Channel<String>`，已删除状态字段与 `ConsumeMessage`。
- ✅ 派生状态记忆化：`conflicts` / `progress` 改用 `derivedStateOf`。
- ✅ 时钟注入 + 真实时间源：单调时钟 + `SyncElapsed`；窗口最小化 / 切后台自动暂停。
- ✅ 出题达标：`generate` 重试至目标空格（测试断言 4 档达标）。
- ✅ 体验项：数字键剩余计数角标、撤销 / 重做可用态。
- ✅ 设置持久化：`SaveCodec` v2 承载三项偏好、兼容 v1，且跨"再来一局"保留。
- ✅ 通关墨框 + 再来一局（复用 `InkOverlay` + 落纸动画，未引入新依赖）。
- ✅ 首页三入口键盘导航（↑↓ / Enter / Esc，含难度与退出确认面板）。
- ✅ 打包收口：ASCII `packageName` + vendor/description/图标，`packageMsi` 实测产出安装包。
- ✅ 候选提示开关：**所有空格**用**半透明灰**数字显示规则允许的候选（第 4 项设置，默认关闭）。
- ✅ 悬浮输入面板：鼠标 / 触控笔点空格就地弹出，**只列该格可填的数字**（键 48dp、尺寸自适应；位置 / 尺寸 / 开合是纯函数 + 单测；手指不弹）。
- ✅ 触屏方案定型：常驻底部键盘 + 同一套空心候选；命中区放大与候选预览条留到 M2。
- ✅ 顶部抽屉统一覆盖层（第五轮）：`TopDrawer` + `TopDrawerController` + `TopDrawerKeys`；难度 / 退出确认 / 通关结算全部迁入，
  `InkOverlay` 删除；抽屉打开时未处理的键一律吞掉（解决"覆盖层 + 快捷键"冲突）；
  新增 `TopDrawerKeysTest`（9）+ `TopDrawerUiTest`（4，真实组合 + 按键注入），测试总数 40 → **53**。
- ⏳ 续局体验增强：首页「继续游戏」显示难度与进度摘要（当前只有置灰/可用两态）。
- ⏳ `legalMask` 下沉到状态层（目前按盘面 `remember`，未进入派生状态）。

**P2（产品化，对应 M3 / M4）**
- 战绩数据（不再做占位页）：按难度的最佳 / 平均用时、通关率；记录页需按墨线风格重新设计后接入。
- 「夜墨」反色主题（深底 + 浅墨）与设置页。
- 第二平台（Web / iOS）+ 逐格无障碍语义。
- 难度模型升级为技巧等级（`docs/05` §4 Tier 2）。

**不做**（详见 `docs/05` §6）：不引 DI / 导航 / ORM 框架、不为跨平台提前拆模块、不混用第二套 UI 组件库、
不做必须联网的核心玩法、不恢复对弈 / 联网 / 社交入口。

---

## 5. 环境与构建备忘

- **`JAVA_HOME` 必须是 JDK 17–24**（本项目用 21）。`jvmToolchain(21)` 复用该 JVM，**仓库内不写死本机路径**；
  本机已在**用户级** `~/.gradle/gradle.properties` 同时声明 `org.gradle.java.home` 与 `org.gradle.java.installations.paths`，
  因此即使 `JAVA_HOME` 被改错（如指向 JDK 25），守护进程仍按 JDK 21 启动——已实测 `BUILD SUCCESSFUL`，
  报错只有一串版本号（如 `25.0.4.1`）是 Gradle 8.12 遇到 JDK 25 的典型表现。
- 依赖已缓存，日常加 `--offline` 更快：`.\gradlew.bat :composeApp:jvmTest --offline`。
- `build/` 与 `composeApp/build/` 属可再生产物，可随时清理（MSI 打包需联网重下 WiX 工具集约 99 MB）。
- 打包：`.\gradlew.bat :composeApp:packageMsi` 产出 `composeApp/build/compose/binaries/main/msi/SudokuInk-2.0.0.msi`（约 60 MB）；
  `createDistributable` 产出 `binaries/main/app/SudokuInk/`（自带 JRE）。
- 实机冒烟：`powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke-e2e.ps1`（先 `createDistributable`）。
  它把窗口 `HWND_TOPMOST` 钉在 (100,100) 1180×900，**每次注入前断言前台是应用**（否则中止），按窗口矩形截图，
  覆盖难度抽屉 / 退出确认 / 通关抽屉三条流，并自动备份恢复 `~/.sudoku-ink/save.txt`。
  **教训**：不置顶就注入按键/点击，桌面有别的窗口时会打到别人的窗口上（点击落到过浏览器）；
  方向键必须带 `KEYEVENTF_EXTENDEDKEY`；脚本保持 **ASCII-only**（PS 5.1 无 BOM 时按 ANSI 解析中文会崩）。
- 图标**一处生成、三处使用**：`tools/make-icon.ps1` 同时产出
  ① 打包图标（`composeApp/icons/sudoku.ico` / `sudoku.png` → jpackage `iconFile`）、
  ② 运行时窗口图标（`composeApp/icons/sudoku-NN.png` 作为 jvmMain 资源进 jar，`main.kt` 设置）、
  ③ 对外交付（`assets/icon/` 各平台规格 + `source/sudoku.svg` 矢量源）。
  改图标只需重跑脚本 + 重新打包；设计与尺寸规范见 `docs/06-应用图标设计.md`。
- 应用存档在 `~/.sudoku-ink/save.txt`（纯文本，可手动删除以清空续局）。
- `archive/**/node_modules` 与 `archive/**/dist` 已在 `.gitignore` 中忽略，勿再入库。

---

## 6. AI 协作与提交自检

- **单任务单会话**：一个任务一个主题；跨目标（桌面 / Android）改动拆开。
- **精准投喂**：先 `@folder composeApp/src/commonMain` 预热，再用 `@file` / `@code` 定位；方法见 `docs/04`。
- **负面约束随需求一起给**：把本文件 §0 的相关红线直接写进需求里。
- **提交前自检清单**：
  1. `.\gradlew.bat :composeApp:jvmTest --offline` 全绿（71 项）；改过 `state/` 或 `core/` 时必须补/改用例；
     动过字体 / 资源时跑 `BundledFontsTest`（字体在、family 对、中文/数字各归其位、**数字族的中文兜底链还在**）；
     动过棋盘绘制 / 语义层时要跑 `BoardCanvasUiTest`（81 节点 + 点击穿透）；
     动过覆盖层 / 键盘映射时，跑 `tools\smoke-e2e.ps1` 并看截图；
  2. 无新增色相、无裸色值 / 魔法尺寸（§0 6–8）；
  3. 四档窗口肉眼验收：1180×900 / 800×600 / 500×1000 / 1024×768；
  4. 键盘路径可走通：首页 ↑↓ + Enter、对局内方向键 / 数字 / Ctrl+Z/Y、`Esc` 收浮层、通关后 Enter / Esc；
  5. 鼠标路径可走通：点空格弹悬浮面板 → 点键填数 → 面板消失；开关切换后棋盘立刻有反馈；
  6. 动过打包配置（`nativeDistributions`）→ 真跑一次 `packageMsi` 并核对产品名与图标；
  7. 受影响的文档已同步更新（`docs/01`–`05` 与 README 对应章节）。
