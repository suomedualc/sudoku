# 数独（手写纸 · 简约油墨）代码审查规则 · v1.0

> 本文件是**代码审查（CR）专用**的系统层上下文，与 `sudoku-dev.md`（开发纪律）配套：
> 开发纪律告诉 AI"怎么写"，本文件告诉 AI"怎么查"。审查结论必须**有据**（文件 + 行号 + 机制推导），
> 不做印象式评价；与开发纪律冲突时以 `sudoku-dev.md` 为准。
> 沉淀来源：第 1 轮 miuix 版全量审查（3 个 P0）、第 36 轮 P1 达成度核验（9 处修复，2 处高危）、
> 2026-10-07 全量复审（14 项发现，双验证员交叉确认）。
> 读法：每次 CR 先过 §1 通用纪律；按改动层跳到 §3 对应小节；§4 是"历史踩坑模式库"，
> 查出的问题先对照它确认是否为**已接受的已知取舍**（不要把设计决定当缺陷报）。

---

## 0. 严重度定义与输出要求

| 级别 | 定义 | 处置 |
|---|---|---|
| **P0（高危）** | 功能不可用 / 数据丢失 / 崩溃 / 无障碍或规则红线被破坏 | 必须修，阻塞合入 |
| **P1（应修）** | 功能性 bug（如语言切换后提示用旧语言）、红线违规且用户可感知（英文界面混中文） | 本轮修或立即排期 |
| **P2（建议）** | 死代码 / 过时注释 / 文档漂移 / 防御性校验缺失 | 择机修，允许挂账 |

输出要求：问题清单用表格（编号 / 标题 / 建议 / `文件:行` 链接）；多行问题给 `[start, end]` 区间（≤100 行）；
跨文件重复问题给一条代表性意见并列出全部位置；**禁止**纯表扬、无行动建议、以及"可能 / 也许"式评论。
同一问题先对照 §4 模式库——历史已判定"不成立"的两条（出题卡顿、严格模式误伤笔记）不要再报。

---

## 1. 通用审查纪律（每次 CR 必过）

1. **证据先行**：评论前必须读完整文件（而非只看 diff 片段）；依赖调用点 / 生命周期的问题，追到调用方确认。
2. **分层单向依赖**：`core/`（只许 kotlin-stdlib + kotlin.random.Random）→ `state/`（禁 Compose / 平台 API，
   reducer 纯函数：同 state + action + rng ⇒ 同结果）→ `ui/`（唯一 Compose 层）→ entrypoints（仅装配）。
   下层 import 上层 = P0。
3. **数组纪律**：`Board` / `notes` 是 `IntArray`——`data class equals` 是引用比较，禁用 `==` 做业务判断；
   状态变更必须 `copyOf()`，发现就地修改 = P0。
4. **一次性提示走事件**：reducer 只发 `Msg` 语义键（`Reduction(state, message)`）→ ViewModel 事件通道 →
   Snackbar。提示不得塞回 `GameState`；UI 文案不得进 `state/`（见 §3.4 的 `keyTokenLabel` 反例）。
5. **测试同步义务**：改 `core/` 补 `commonTest` 用例；改 `state/` 补 `GameReducerTest` / `GameStoreTest`（固定种子）；
   改绘制 / 语义层跑 `BoardCanvasUiTest`；改配色跑 `InkThemeTest`。当前基线 **137 项**（`commonTest` 116 + `jvmTest` 21），
   数量变化要解释原因。
6. **文档同改**：改 `state` 对外类型 / Action / Screen → 同步 `docs/01` §5；改视觉 → `docs/02`；
   改流程 / 测试数 → `docs/03`。**文档漂移按 P2 记**（本项目反复发生，见 §4-M8）。
7. **编译性假设**：Kotlin 是编译型语言，除非 diff 自证，不报"可能编不过"；`androidMain/` 已参与编译（2026-10-07 起），
   其腐化会直接构建失败。

---

## 2. 按层审查重点

### 3.1 `core/`（Sudoku.kt · CellCursor.kt）

- 出题不变量：`generate` 兜底分支必须**连着产生该题的终盘**一起返回（"题目 ⊆ 终盘"）；难度 `targetBlanks`
  靠 `DIG_ATTEMPTS=6` 重试达标，改动重试策略必须同步 `SudokuTest` 的达标断言。
- `legalMask` 语义：**包含该格自身值**（"再按同数字 = 清除"依赖它）——历史遗留：KDoc 写反成"排除自身值"（P2 待修）。
- `CellCursor` 三条硬规则：方向键**逐格可停已填格**（"回去改一格"不许藏修饰键后）、Shift 跳空格、
  自动跳转落点**必须是空格**。改跳转策略前先读 `CellCursorTest` 的两条策略对比断言。
- `conflictFlags` 依赖取值 ≤ 9（`counts[10]`）；`GROUPS` 是静态表——审查时不要接受"每次 buildList 重建"的回退。
- `formatDuration` 手写拼接（避免平台格式化 API）。

### 3.2 `state/`（Reducer / Store / ViewModel / KeyMap / Stats）

- **Snapshot 完整性**：快照必须覆盖"所有会被改的对局字段"（`current/notes/hintUsed/hintsCount/revealed`）。
  漏一个就会出现"`hintUsed` 回退而 `hintsCount` 不回退"式矛盾。`elapsed` / `outcomeCounted` **刻意不进快照**
  （时间不倒流；防"撤销再赢重复计胜"）——这是设计，不是缺陷。
- 无变化的输入**不压栈**（空格按擦除、同数字取消已处理）；`redo` 后统一走 `settle()` 重推 `settled`；
  两个栈都要 `takeLast(UNDO_LIMIT)` 截断。
- **新偏好七处同步清单**（漏一处就是"存盘里有、重启后没了"）：
  ① `GameState` 字段 ② `GameSettings` ③ `SaveCodec.encode` ④ `SaveCodec.decode`（缺行取默认，v1/v2 兼容）
  ⑤ `SaveFile.toState()` 逐字段映射 ⑥ `NewGame` 显式继承 ⑦ `GameStoreTest.settingsSurviveRestartAndNewGame`。
  当前七项偏好：strictMode / showNotes / noteMode / hintCandidates / darkMode / keyMap / language（+ stats 独立一行）。
- `KeyMap` 不变量：1–9 拒绑（`accepts`）、一键不兼两职（`bind` 先复位撞车键）、退格 / Del / 方向键兜底不可锁死。
  已知缺口：`decode` 不复检 accepts（手改存档可绕过，P2 挂账）。
- `GameViewModel`：只改选中格 / 只报时的动作**不落盘**（Select/Move/Deselect/FocusFirstEmpty/SyncElapsed）；
  计时只走"单调时钟 + SyncElapsed"，reducer 不读时钟；暂停 / 离开对局页 / 最小化必须停表（`syncAnchor`）。
- 统计只存**事件累加值**，派生值（胜率 / 平均）不落盘；记账点：`recordWin`（`outcomeCounted` 防重放）、
  `recordAbandon`（换掉有进展的局 / 看答案）、`recordMove`（仅真实填入）。

### 3.3 `ui/theme` + `ui/components`（视觉与绘制）

- **只有纸与墨**：出现任何色相 / 第三方语义色 = P0。颜色字体只许 `Ink.kt`（getter 形式，禁 `val X = Color(...)` 缓存——
  否则切夜墨停在旧色）；尺寸只许 `DesignTokens.kt`。调用点裸色值 / 魔法尺寸 = P1（绘制内部亚像素微调
  如 `radius + 0.8f`、对齐补偿 1.5f 可豁免）。
- 字号只走 `Ink.Type` 六档；动效时长只走 `DesignTokens.Motion` 四档 + `CursorLandMs`，**且必须过
  `motionDurationMs()`**（reduced-motion 时归零，不做半速）。新增 `animate*AsState` / `tween` 没过它 = P1。
- 手绘抖动：`inkLine / inkRoundRect / inkHatch` 的 seed 必须由内容派生（纯函数）；出现 `Random()` 抖动源 = P0（重绘闪动）。
- 绘制尺寸用 dp / `toSp()`；`DrawScope.size` 是像素，`(px).sp` 直接报 P0（高 DPI 数字溢出，历史 P0-2）。
- 文本缓存键含数字 + 颜色 + 字号 + 字族（`InkTextKey`）；整盘 alpha 显影**不许**混进缓存键。
- 语义网格**只能 `weight(1f)` 等分**，`fillMax*(1f/9f)` = P0（逐格缩水 65%，读屏与画面错位，历史 #12）；
  验证必须点**最后一格**（前几行会蒙对）。
- 暂停遮题必须 `clearAndSetSemantics` 清 81 节点（视觉盖纸但读屏念答案 = P0）。
- 浮层纪律：覆盖层只用 `TopDrawer`；页面根 `onPreviewKeyEvent` **先** `drawer.handleKey`；纸片必须自带
  `detectTapGestures {}` 吞点击（否则点纸上空白 = 点遮罩误关）；通关抽屉 `dismissible = false`。

### 3.4 `ui/screens` + `App.kt`（装配与交互）

- **i18n 红线（本项目当前最高频违规源）**：界面上任何可见文字、读屏文案、悬停提示一律走 `ui/i18n/Strings`
  中英双份；reducer 只发 `Msg`。审查动作：对 commonMain 做**中文字面量扫描**（排除注释 / i18n 目录 /
  句子语料 / `AppLanguage.label` 两条例外），命中即 P1。历史案例（2026-10-07 一轮抓出 7 处）：
  顶栏"已暂停"（字典有 `pausedLabel` 却没用）、`a11yTitle = "键位设置"`（字典有 `keymapA11y`）、
  浮动条 "撤销"/"重做"（**字典里根本没有键**）、悬浮面板"本格无可填数字"、`keyTokenLabel` 在 state 层返回
  "空格"/"退格"、`notesText` 硬编码"、"分隔符、句子区"答案："。
- **Compose 捕获 / key 陷阱**：`LaunchedEffect(Unit)` 内引用会变的局部值 = P1（语言切换后提示仍用旧语言，
  App.kt 实例）；修法 `rememberUpdatedState` 或循环内现取。同类：`pointerInput(Unit)` 捕获 lambda、
  协程里读 `size.height`（首次布局前是 0，必须 `onSizeChanged` 记账）。
- `remember` 的 key 用 `IntArray` 时是**引用相等**（`game.current` 每次落子换新实例所以可用；换成语义相等
  需求时必须换 key 或 `contentEquals`）。
- 悬停判据用 `change.pressed == false`（Enter/Exit 或未按下），只认 `Move` 会漏"一步跨进棋盘"。
- 棋盘外"点空白清临时标记"靠**下层兄弟节点**接点击（外层挂手势会吃掉棋盘悬停）；清的是 `Deselect`
  （选中框 / 高亮派生自它），**不清冲突排线**。
- 键序硬规则：抽屉（模态吞键）→ 悬浮面板 Esc → 棋盘；`TopDrawerKeys` 只在 KeyDown 调 drawerHandler
  （KeyUp 吞掉不派发——防止 Enter 二次触发）。
- `Ink.setDark` 在组合期写入（App.kt）：**已接受的已知取舍**（守卫 + 同帧防闪烁），不报。

### 3.5 entrypoints 与工程

- `nativeDistributions`：`packageName` 纯 ASCII 无空格；改过打包配置必须真跑 `packageMsi` 验证（只看
  `createDistributable` 通过不算）。
- 图标两处设置缺一不可：jpackage `iconFile`（exe 文件图标）+ `main.kt` 的 `window.iconImages`（窗口 / 任务栏）；
  资源读取用 `Class.getResourceAsStream("/x.png")`（ClassLoader 版不接受前导 `/`，静默返回 null）。
- 存档写入必须**同目录临时文件 + ATOMIC_MOVE**（含退化分支）；直接 `writeText` 覆盖 = P0（半份存档风险）。
- 字体：霞鹜文楷（正文）+ Nunito（数字，**必须保留霞鹜文楷回退链**，`digitFontChain().size == 2`）；
  缓存 `~/.sudoku-ink/fonts/` 按字节数校验；OFL 原文随包。
- 依赖：Kotlin 与 CMP 由 `libs.versions.toml` 同一版本目录锁定（2.4.0 + 1.11.1），**无第三方 UI 库**；
  新增依赖必须登记理由。

---

## 3. 验证命令（审查结论的落地检查）

```powershell
.\gradlew.bat :composeApp:jvmTest --offline      # 137 项基线，全绿才允许"无问题"结论
powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke-e2e.ps1 [-Flow <名字>]
# 10 条流：menu / exit / win / theme / bar / ux / keys / keymap / stats / lang（置顶 + 固定坐标，自动截图）
.\gradlew.bat :composeApp:createDistributable    # 改过运行期行为后重建便携版实机试玩
.\gradlew.bat :composeApp:packageMsi             # 仅在动过 nativeDistributions 时必须真跑
```

四档窗口肉眼验收：1180×900 / 800×600 / 500×1000 / 1024×768。改配色必须跑 `InkThemeTest`
（两套主题前三级 ≥ 4.5:1；`Alpha.Hint` 是唯一低于 AA 的例外——冗余通道）。

---

## 4. 历史踩坑模式库（CR 时对照，报新账不翻旧账）

| # | 模式 | 一句话判据 | 现状 |
|---|---|---|---|
| M1 | `aspectRatio` 高度优先 | Column 非 weight 子项拿"剩余"高度 → 棋盘吃满、键盘 0 高 | ✅ 已修（棋盘 side = min(宽, 高-预留)），回退即 P0 |
| M2 | px 当 sp/dp 用 | `(cellPx * 0.5f).sp` 在 density=2 下数字溢出 | ✅ 已修（toSp / dp 令牌） |
| M3 | 无键盘 / 无语义 | Canvas 无 semantics、`Move` 死代码 | ✅ 已修（81 节点 + 全键位），回退即 P0 |
| M4 | 消息塞进状态 | 相同文案被等值比较吞掉 | ✅ 已修（Reduction + Channel + 自增 id） |
| M5 | 派生状态每秒重算 | conflicts/progress getter + 心跳 → 整屏重组 | ✅ 已修（derivedStateOf） |
| M6 | 存档非原子写 | writeText 覆盖留半份存档窗口 | ✅ 已修（tmp + ATOMIC_MOVE） |
| M7 | 每动作全量落盘 | 移动光标也重写整个文件 | ✅ 已修（persistsToDisk 白名单） |
| M8 | 文档漂移 | rules/docs 的数字与实现不符（流数、入口数、设置项数、测试数） | ⚠️ **反复发生**：见 §5 开放问题；CR 必查 |
| M9 | `LaunchedEffect(Unit)` 陈旧捕获 | 循环里用到的"会变的值"在首帧闭包里固化 | ⚠️ App.kt 提示循环存在（P1） |
| M10 | i18n 字面量逃逸 | 可见文字 / 读屏绕过 Strings 字典 | ⚠️ 一轮抓出 7 处（P1），新增 UI 必查 |

已判定**不成立**（勿再报）：出题同步执行卡 UI（实测 0–1ms）；严格模式拒绝冲突笔记（设计内语义）；
`redo` 截断撤销栈（防御性，实际上限不可破）；组合期写 `Ink.setDark`（守卫 + 防闪烁的刻意取舍）。

---

## 5. 开放问题清单（2026-10-07 全量复审沉淀；同日已完成一轮修复并全量验证）

> ✅ = 已修复（jvmTest 137 项全绿），CR 时如复现按 P1 报回归。

**已修复（2026-10-07 修复轮）**
1. ✅ `App.kt` 提示循环语言陈旧捕获——改为循环内 `stringsFor(viewModel.state.language)` 现取。
2. ✅ 顶栏 "已暂停" → `strings.pausedLabel`。
3. ✅ 浮动条 "撤销"/"重做" → 字典补 `undo` / `redo` 两键并引用。
4. ✅ 悬浮面板 "本格无可填数字" → 字典补 `padNoCandidates`。
5. ✅ `keyTokenLabel` 迁入 `ui/i18n/Strings.kt`（`Strings` 扩展，中英各取 `keySpace` / `keyBackspace` / `keyDel`）；state 层不再携带 UI 文案。
6. ✅ 键位抽屉 `a11yTitle` → `strings.keymapA11y`。
7. ✅ 读屏笔记分隔符 → `Strings.notesSeparator`（中文"、" / 英文", "）。
8. ✅ 句子区 "答案：" → `Strings.riddleAnswerPrefix`。
9. ✅ `Sudoku.legalMask` KDoc 改正为"包含该格自身值"。
10. ✅ `Strings.kt` 删除对不存在 `StringsTest` 的虚假引用（改为强调"中英同批给齐，无自动守卫"）。
11. ✅ `KeyMap.decode` 逐槽位复检 "1–9 拒绑"（撞数字的槽位回落默认键位，`KeyMapTest` 补断言）。
12. ✅ 删除 `Difficulty.label`（core 不再携带 UI 文案；测试失败消息改用 `name`）。
13. ✅ 文档漂移批：rules（七项设置 / 五入口 / 十条流 / 新局继承七项偏好 / GameSettings 七字段）、
    docs/01（五入口）、docs/02（五入口 + 随机句子 + 副标题）、docs/03（十条流 + 七项设置）、
    docs/05（删除与 L19 矛盾的重复"三入口"行）、README（功能节与键盘节补齐五入口）。

**仍开放（挂账，未修）**
14. `MenuScreen` 焦点模型与游标模型并存（Tab 聚焦后 Enter 走游标项）。超出"不影响核心功能"边界，待拍板。
    （androidMain 已随 2026-10-07 P0 启动落地并模拟器验证，原挂账关闭。）
15. `Strings` 中英键一致性无自动化守卫（本次修复删除了虚假注释；如需真正守卫需引入反射或代码生成，暂缓）。
18. **窄屏适配缺陷（Android 模拟器测试发现，docs/08 §4-D1，P1）**：对局页功能区与棋盘同宽的中轴设计
    在 ≤~430dp 下失效——数字键盘压成 ~19dp 细条（T4/T6 用例失败）、开关行勾选框溢出、顶栏读数截断。
    修复方向：窄屏断点下键盘整页宽/折行、开关 2×2、顶栏减档；补窄屏 UI 测试与冒烟档。
19. Android 返回键导航语义未实现（v1 返回=退出，对局自动存盘）；自适应图标资源已生成未接线。

**第二轮修复（2026-10-07，用户三项需求）**
16. ✅ 墨条"消失时间随点击次数累加"——显示从"挂起排队"改**顶替式固定时长**（新提示立即顶掉当前条，
    每条恰停 2.4s；计时在 `InkSnackbarHost` 协程；`InkSnackbarHostUiTest` 用测试时钟钉住）。
17. ✅ 首页语料升级为**本地语料库文件**（`jvmMain/resources/corpus/sentences.txt`，开发期收集、运行时读取、
    离线可用；`SentenceCorpusTest` 契约 + actual 三层兜底）；候选提示"灰度小数字画在空格内"经实机验证本已实现。

---

## 6. 审查流程（建议按此顺序执行）

1. **定范围**：用户指定 diff / 文件 / 分支；未指定时先 `git status` + `git diff` 看工作区改动。
2. **读纪律**：过一遍 `sudoku-dev.md` §0 红线 + 本文件 §1。
3. **按层扫描**：改动落在哪层就走 §3 对应小节的检查单；跨层改动逐层过。
4. **模式对照**：每个发现对照 §4——是回归（升级严重度）、新问题、还是已接受取舍（不报）。
5. **交叉验证**：≥3 个发现时派 2 个子代理并行独立复核（存在性 / 行号 / 严重度 / 误报），
   2/2 采信、1/2 带注采信、0/2 剔除。
6. **输出**：mermaid 图总结改动流（业务流 + 技术流各一，复杂时）→ 问题表格（编号 / 标题 / 建议 / 链接）→
   让用户勾选要修的项。修复后必须跑 §3 验证命令并勾掉 §5 清单对应条目。
7. **归档**：新一轮 CR 的结论回写本文件 §5（修掉的划掉，新发现的补入），保持"开放问题清单"是最新真相。
