# 接手基线（2026-10-24 · 第 3 轮接手实测）

> 本文件只记录**本轮亲手跑出来**的结果。与 `docs/14` §6 一致即视为文档可信。
> 用途：下次接手不必重跑全量验证，先读本文件；若与之不符，以新实测为准并更新本文件。

---

## 1. 环境实测

| 项 | 实测值 | 判定 |
|---|---|---|
| `JAVA_HOME` | `D:\env\_SDK\versions\jdk_versions\jdk` → OpenJDK **21.0.12.1** LTS | ✅ 落在 17–24 |
| Android SDK | `local.properties` → `sdk.dir=D:/env/_SDK/Android-SDK` | ✅ |
| Gradle wrapper | 8.14.4，`--offline` 全部可用 | ✅ 无需联网 |
| git HEAD | `e2845ac docs: 更新交接文档（第二轮接手后的新格局）` | ✅ |
| 工作区 | `git status` 干净（本文件除外） | ✅ |
| 构建目标 | `androidTarget{}`(L23) + `jvm()`(L30)，**无 iOS/Web** | ✅ 与文档一致 |

---

## 2. 验证基线（全部 `--rerun-tasks` 强制真跑，不信 `UP-TO-DATE`）

| 命令 | 实测 | 文档 | 判定 |
|---|---|---|---|
| `:composeApp:jvmTest --offline` | **161 项 / 0 失败** | 161 | ✅ |
| `:composeApp:testDebugUnitTest --offline` | **133 项 / 0 失败** | 133 | ✅ |
| 去重合计 | **168** | 168 | ✅ |
| `compileKotlinJvm :compileDebugKotlinAndroid` | SUCCESSFUL | — | ✅ |
| `createDistributable` | SUCCESSFUL，产出 `SudokuInk.exe` | — | ✅ |
| 冒烟 **11 条流逐流单跑** | **11/11 exit=0，零 WARN**，85 张截图全 1770×1350 且通过纸墨判据 | 11 | ✅ |

**11 条流实测明细**（逐流起独立进程 + 流间 sleep 2s，避免 `Pin` 抢前台）：

| 流 | exit | WARN | 截图数 | 流 | exit | WARN | 截图数 |
|---|---|---|---|---|---|---|---|
| menu | 0 | 0 | 3 | keymap | 0 | 0 | 9 |
| exit | 0 | 0 | 1 | stats | 0 | 0 | 3 |
| win | 0 | 0 | 3 | lang | 0 | 0 | 4 |
| theme | 0 | 0 | 3 | reveal | 0 | 0 | 4 |
| bar | 0 | 0 | 2 | keys | 0 | 0 | 8 |
| ux | 0 | 0 | 4 | | | | |

> ⚠️ 首次 `jvmTest` 返回 `UP-TO-DATE`（**等于没跑**）。必须加 `--rerun-tasks`，
> 或读 `composeApp/build/test-results/jvmTest/*.xml` 的 `tests="N"`。
> 计数用正则取 `<testsuite … tests="N">`，**不要用 `[xml]`**（根节点 `name` 属性会遮蔽，`AGENTS.md` §12.4）。

---

## 3. 代码量与文档漂移核对

| 项 | 实测 | 文档声称 | 判定 |
|---|---|---|---|
| Kotlin 文件数 | **84** | 84 | ✅ |
| Kotlin 总行数 | **12,312** | 12,312 | ✅ |
| `AGENTS.md` | **26,850 B** | 26,850 B | ✅ |
| `_archive/codebuddy-rules-20261024/` | 40,792 + 24,003 = **64,795 B** | 64,795 B | ✅ |
| `state/GameAction` 子类型 | **23** | 23 | ✅ |
| 冒烟流 `ValidateSet` | **11** + `all` | 11 | ✅ |
| `GameScreen.kt` | 632 行 | 664 | ⚠️ 口径差（`Measure-Object -Line` 不计末行无换行） |
| `InkWidgets.kt` | 1,021 行 | 1,078 | ⚠️ 同上口径 |

---

## 4. 🔴 本轮新增发现（文档未记录，接手者必读）

### 4.1 ✅ P1（已修）：冒烟脚本默认 `-ExePath` 是失效旧路径

| 项 | 值 |
|---|---|
| 位置 | 原 `tools/smoke-e2e.ps1:42` |
| 原默认值 | `D:\LESSON\sudoku\composeApp\build\compose\binaries\main\app\SudokuInk\SudokuInk.exe`（`Test-Path` = **False**） |
| 受影响文档 | `docs/14:273`、`AGENTS.md:173`、`README.md:156`、`docs/03:180` — 全部示例都不带 `-ExePath` |
| **修复** | 默认改为 `''`，在 `$ErrorActionPreference` 后按 `Split-Path -Parent $PSScriptRoot` 推导 |
| **验证** | 从 `C:\` 调用、**不带 `-ExePath`** → `exit=0`，3 张截图；PS 解析器语法 OK；改动区 **0 个非 ASCII** |

### 4.2 P2（红线违例，持续存在）：`state/` 层 import 了 Compose

`AGENTS.md:19` 原文：**「`state/` 禁 Compose / 平台 API；`GameReducer` 必须纯函数」**

| 违例位置 | 内容 |
|---|---|
| `state/GameViewModel.kt:3-6` | `androidx.compose.runtime.derivedStateOf` / `getValue` / `mutableStateOf` / `setValue` |

**影响评估（已实测，勿夸大）**：
- `state/` 其余 8 个文件 **0 违例**（`GameReducer` / `GameState` / `GameStore` / `GameAction` / `Msg` / `KeyMap` / `GameStats` / `Language` 全干净）；
- `compose.runtime` 是 KMP **公共**库（非平台专属），所以编译、双端运行、单测均不受影响；
- `GameViewModel` **已被 commonTest 覆盖**（`GameStoreTest` 8 处、`GameClockTest` 1 处），可测性未受损。

**结论**：这是**规则与实现的历史性偏差**，不是功能缺陷。两种处置待定：
① 承认 `GameViewModel` 是"状态容器"而非纯逻辑，修订红线措辞把它排除；② 抽 `MutableState` 接口注入，使 state 层零 Compose。
**改之前必须先确认取哪一种，不要擅自大改。**

### 4.3 ✅ P2（已修）：平台接缝是 **7 个**，文档原写"6 个"

已改：`docs/14` 三处 6→7（L87 / L138 表 + 补第 7 行 / L317）、`AGENTS.md:102` 纠正 `GameStore` 不是 expect/actual 并补 `Strings`。
修复后全量回归：`jvmTest 161/0`、`testDebugUnitTest 133/0`、双端编译 SUCCESSFUL、冒烟 `keys` 流 `exit=0`。

**实测 7 个**（`expect` 全量 grep + 两端 `actual` 逐一核对，jvm/android 各 1）：

| # | 接缝 | 位置 |
|---|---|---|
| 1 | `Modifier.safeAreaPadding()` | `ui/theme/SafeArea.kt:19` |
| 2 | `systemAppLanguage()` | `ui/i18n/Strings.kt:521` |
| 3 | `prefersReducedMotion()` | `ui/theme/Motion.kt:16` |
| 4 | **`readSentenceCorpusText()`**（文档漏记） | `ui/i18n/Sentences.kt:52` |
| 5 | `textFontFamily()` | `ui/theme/Ink.kt:251` |
| 6 | `digitFontFamily()` | `ui/theme/Ink.kt:254` |
| 7 | `SystemBackHandler()` | `ui/platform/SystemBack.kt:12` |

> 漏记原因：`readSentenceCorpusText()` 由 commit `560831f`（统计/随机句子/多语言）引入，晚于接缝表定稿。
> **另**：`AGENTS.md:102` 把 `GameStore` 列为接缝，但 `state/GameStore.kt:51` 明确写它是**端口注入、不是 expect/actual`** —— 两处表述需统一。

### 4.4 已澄清的两个**假警报**（我自己先判错过，记下来防止后来者重犯）

| 现象 | 我最初的误判 | 真相 |
|---|---|---|
| 启动 `SudokuInk.exe` 后 8.4MB / 5 线程 / `MainWindowHandle=0` | "启动失败、无窗口" | jpackage **两级进程**：launcher(8.4MB) 转发给真正 JVM 进程（**326.6MB / 77 线程 / 窗口标题 `数独 Sudoku`**）。判据：找 300MB 级、70+ 线程那个 |
| `smoke_37` 截图右半"空白"、两侧透出背景文字 | "严重 UI 布局缺陷 / 窗口尺寸错" | 逐像素实测：纸色 `R247 G245 B240` 一直延伸到 **x=1758**（图宽 1770），内容宽度正确。透出仅在 **x=0..9（约 10px）**，是 `CopyFromScreen` 抓窗口边框/阴影的偏移，**不影响 UI 判定** |

> 两条都是"看起来像产品 bug、实为工具/进程语义"。**判 UI 缺陷前必须先做像素级测量。**

---

## 5. 桌面端真跑记录

| 进程 | 内存 | 线程 | 窗口 |
|---|---|---|---|
| `SudokuInk.exe`（launcher） | 8.4 MB | 5 | 无 |
| JVM 主进程 | **326.6 MB** | **77** | `数独 Sudoku`，hwnd 有效 |

冒烟截图全部 **1770×1350** = 1180×900 dp × 1.5 DPI，**正是判据要求的正确尺寸（非残窗）**。
视觉抽查（`smoke_6_win_drawer` / `smoke_37_lang_menu_zh`）：纸墨单色、无彩色语义色、通关抽屉/双键盘/偏好开关/readscreen 提示齐全，**符合红线 6–8**。
`~/.sudoku-ink/save.txt` 运行前不存在（首次运行未落盘，预期）。

---

## 6. 红线实测结论

| 红线 | 实测 | 判定 |
|---|---|---|
| `core/`+`state/` 零平台依赖（禁 IO/时钟/UI/平台） | 仅 `GameViewModel` 4 处 Compose import | ⚠️ 见 §4.2 |
| 组件禁裸色值（`Color(0x…)` / `Color.Red` 等） | `ui/` 非 theme 目录 **0 处** | ✅ |
| i18n：可见文字全走 `Strings` | `commonMain` 中文字面量 8 处，**全部白名单**（`require` 异常消息 5、`AppLanguage.label` 2、`GameReducer` 注释 1） | ✅ |
| Android 存档进程级单例 | `MainActivity.kt:80 sharedStore()` + `companion cachedStore`，注释明写"不能放 remember" | ✅ |
| 存档原子写 | `AndroidGameStore.kt:35-43` `ATOMIC_MOVE` + 回退 `REPLACE_EXISTING`，同目录临时文件 | ✅ |
| `versionCode` 由 `versionName` 推导 | `2.1.0` / `20100`（= 2×10000+1×100+0） | ✅ |
| SDK 档位 | `compileSdk 36` / `minSdk 26` / `targetSdk 36`；`jvmTarget 17`（Android）、桌面 21 | ✅ |

---

## 7. 代码层面确认的不变量（读源码，非文档转述）

| 不变量 | 位置 | ✅ |
|---|---|---|
| reducer 纯函数，rng 注入 | `GameReducer.reduce(state, action, rng)` | ✅ |
| 提示走 `Reduction.message` 事件，不进 State | `GameReducer.kt:16-20` | ✅ |
| `legalMask` 含该格自身值（支持"再按同数字=清除"） | `Sudoku.kt:255` | ✅ |
| 数组用 `copyOf()`，禁 `==` | `GameReducer.place` | ✅ |
| 判胜 = `isSolved && contentEquals(solution)` | `GameReducer.kt:380` | ✅ |
| `outcomeCounted` 不随 Snapshot 回滚，`reset()` 显式清 | `GameReducer.kt:292` | ✅ |
| `Snapshot` 含 `hintsCount` | `GameState.kt:17-23` | ✅ |
| `decode` 失败返回 `null` 不抛 | `GameStore.kt:176-204` | ✅ |
| 计时 = 锚点 + 累计（非每秒 +1） | `GameViewModel.syncAnchor/syncClock` | ✅ |
| 只改选中格的动作不落盘 | `persistsToDisk()` | ✅ |
| `MutexGameStore` 抢锁失败降级只读 + 暴露 `writable` | `GameStore.kt:100-114` | ✅ |
| 自动跳转"宫优先 + 回退读序" | `CellCursor.nextAfterFill` | ✅ |

---

## 8. 发布剩余项（`docs/11` checklist：6 未勾 / 6 已勾）

| 未勾项 | 本机能否做 |
|---|---|
| A3 AAB 打包 + 签名 | ❌ 需密钥决策，**不得自行生成** |
| A5 真机矩阵 | ❌ 无实体设备 |
| 横屏真机验证 | ❌ 无实体设备 |
| 商店素材（512 图标 + 截图 + 中英描述） | ⚠️ 图标可生成，截图需真机 |
| CI（**已决定移除**，非缺陷） | — |
| 字体子集化评估 | ⚠️ 可选优化 |

## 9. 能力边界（做不到就说做不到）

| 项 | 原因 |
|---|---|
| A5 / 横屏真机 | 本机无实体 Android 设备 |
| iOS / macOS 构建 | 本机无 macOS |
| `-Flow all` 一次连跑 | `Pin` 抢前台致 `FOREGROUND LOST`，**必须逐流单跑 + 流间 sleep** |

## 10. 下一步（按优先级，等确认）

1. 修 `tools/smoke-e2e.ps1:44` 默认 `-ExePath` 为相对 `$PSScriptRoot` 路径（**P1，5 分钟**）；
2. 逐流单跑剩余 10 条冒烟流（`exit/win/theme/bar/ux/keys/keymap/stats/lang/reveal`，每条 30–60s）；
3. 裁决 §4.2 红线偏差走"改规则"还是"改代码"，再动；
4. 修 `docs/14` 三处"6 个接缝"→7 个 + 补 `readSentenceCorpusText`（**P2 文档漂移**）；
5. 按 `docs/11` 盘点发布项。
