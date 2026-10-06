# 数独 Sudoku · 手写纸（Kotlin + Compose Multiplatform）

一份 Kotlin 代码实现的单机数独：`core`（算法）/ `state`（状态机）/ `ui`（表现）三层单向依赖，
界面为**手写纸 · 简约油墨**——只有纸与墨两种颜色，边框、网格、勾选、冲突排线全部手绘；
页面骨架、主题上下文与一次性提示由 [miuix](https://github.com/compose-miuix-ui/miuix) 提供。

## 视觉风格

- **只有纸与墨**：纸 `#F7F5F0`、墨 `#1B1A17`（另两级淡墨）。没有色相——冲突用「斜向排线 + 手绘圈」，
  禁用用「虚线框」，可填性用「线重」，模式用「线型」。
- **手绘而非几何**：所有边框、网格、勾选、排线都由 `inkLine / inkRoundRect / inkHatch` 绘制（折线微弯 + 叠一遍淡墨），
  抖动由纯函数种子生成，重绘时线条静止不闪动。
- **手写体**：取本机系统中文字写体（方正硬笔楷书 → 霞鹜文楷 → 楷体 → 华文楷体 → …），取不到时回退衬线体；字体不随包分发。
- **留白优先**：间距节奏比 Material 更疏（16 / 24 / 32 / 48 dp），不靠装饰块分区。
- 主题固定浅色纸面；「夜墨」反色主题位于路线图（`docs/05`）。

## 功能

- **首页三个入口**：`开始游戏`（墨框难度面板：简单 40 / 普通 46 / 困难 52 / 大师 56 空）、
  `继续游戏`（无未完成对局时置灰并附说明）、`退出游戏`（二次确认）。无对弈、联网、社交。
- **棋盘**：9×9 手绘网格，宫线加粗；选中双框、同行列宫淡墨、同值淡墨；冲突格斜排线 + 数字手绘圈；
  给定数字实墨、玩家填入淡墨、候选数小号淡墨。
- **数字输入**：选格后点数字键填入；`擦除` 清空；笔记模式记候选（落子自动清理同行列宫候选）；
  数字键右上角显示"还剩几个"，用完转淡墨框。
- **候选提示**（开关，默认关闭）：打开后**每个空格**都用**半透明灰**小字直接写出"按规则仍可填"的数字；
  自己记的笔记是实墨小字，两者靠墨色深浅区分——不靠颜色。落子后同行列宫的候选会同步刷新。
- **鼠标就近输入**（桌面）：鼠标或触控笔点**空格**，就在该格旁弹出**半透明墨框面板**，
  面板**只列出这一格当前可填的数字**（通常 2–4 个，因此键可以做到 48dp、面板更小更好按），
  点键即填、填完自动收起；方向键换格时面板跟着走，`Esc` 收起。指针无需在棋盘与右侧键盘之间往返。
- **难度与唯一解**：出题先校验唯一解，再按难度挖洞；挖不满目标空格时自动换挖洞顺序 / 终盘重试至达标。
- **对局辅助**：撤销 / 重做（上限 300 步，栈空时按钮转虚线框）、提示（状态行显示「提示 ×N」）、
  重置、暂停（白纸遮题，不泄题）、严格模式。
- **计时**：由单调时钟按真实时间推进；暂停、通关、离开对局页、窗口最小化都会停表。
- **通关抽屉**：填满正确盘面后从顶部滑下结算抽屉，显示用时 / 难度 / 提示次数，可直接「再来一局」（同难度重开）或返回首页。
- **偏好与续局**：严格模式 / 显示笔记 / 笔记模式三项偏好跨对局、跨重启保留；
  未完成的对局自动存盘（桌面 `~/.sudoku-ink/save.txt`），重启后首页即可「继续游戏」。

## 键盘（桌面）

| 场景 | 按键 |
|---|---|
| 首页 | `↑` / `↓` 在三个入口间移动（自动跳过置灰项）、`Enter` / 空格 确认 |
| 抽屉（难度 / 退出确认） | `↑` / `↓` 在条目间移动、`Enter` / 空格 确认、`Esc` 关闭 |
| 对局 | `1–9` 填数、`0` / `Delete` / `Backspace` 擦除、方向键选格（自动跳过给定格）、`N` 笔记、`H` 提示、`P` / 空格 暂停、`Esc` 继续（先收悬浮面板）、`Ctrl+Z` 撤销、`Ctrl+Y`（或 `Ctrl+Shift+Z`）重做 |
| 通关抽屉 | `Enter` 再来一局、`Esc` 返回首页 |

鼠标点击会把键盘高亮同步到被点的项，两种输入方式互不打架；悬浮面板不接管焦点，键盘操作始终可用。
**抽屉是模态的**：打开期间未处理的按键一律被抽屉吞掉，不会穿透到下面的菜单或棋盘（`TopDrawerKeys` 统一裁决）。

## 鼠标与触屏

| 设备 | 数字输入 | 候选数字展示 |
|---|---|---|
| 鼠标 / 触控笔（桌面） | 点空格 → **悬浮面板**（就近，只列可填数字）；右侧常驻键盘同样可用 | 所有空格的**半透明灰候选**（候选提示开启时）、常驻键盘的非法数字转淡墨 |
| 触屏（移动端） | 点格只选中，用**常驻底部数字键盘**输入——不等同于桌面方案：手指会遮住落点附近，浮层只能放到手指以外，收益不如直接用底部键盘 | 与桌面**同一套**半透明灰候选（`core` / `state` 零差异）；M2 追加命中区放大与候选预览条 |

详细规定（面板位置 / 样式 / 消失时机 / 键盘兼容逻辑）见 `docs/02-设计规范.md` §4.1 与 §6.1。

## 技术栈与版本

| 组件 | 版本 | 说明 |
|---|---|---|
| Kotlin | 2.4.0 | 由 Gradle 插件拉取 |
| Compose Multiplatform | 1.11.1 | miuix 0.9.2 直接依赖该版本 |
| miuix | 0.9.2 | 提供主题 / 骨架 / 提示通道 |
| Gradle | 8.12 | wrapper 已入库，无需本机安装 |
| JDK | 21（17–24 皆可） | 编译工具链由 `jvmToolchain(21)` 固定 |

三者（Kotlin / CMP / miuix）**强绑定**，必须整体升降级，详见 `docs/03` §2。

## 架构

```
org.example.sudoku
├── core/         领域内核：位掩码 + MRV 求解、出题、冲突、候选、时长格式化（零平台依赖）
├── state/        应用层：GameState / GameAction / GameReducer（纯状态机）
│                 + GameViewModel（状态容器）· GameStore / SaveCodec（存档端口与编解码）
├── ui/
│   ├── theme/    Ink.kt（纸墨配色 + 手写体 + 手绘原语）· DesignTokens.kt（尺寸）
│   ├── components/  InkWidgets.kt（墨线控件库）· BoardCanvas.kt · NumberPad.kt · FloatingPad.kt（就近输入）
│   ├── screens/  MenuScreen（三入口 + 键盘导航）· GameScreen（双形态 + 通关抽屉 + 键盘）
│   └── App.kt    MiuixTheme + Scaffold + 两页导航 + Snackbar
└── entrypoints
    ├── androidMain/MainActivity.kt
    └── jvmMain/  main.kt（窗口 1180×900 / 最小 940×720）· platform/FileGameStore.kt · ui/theme/InkFonts.jvm.kt
```

单向数据流：`输入（点击 / 键盘 / 心跳）→ GameAction → GameReducer.reduce → GameState → Compose 重组`。
副作用只有三处：提示走事件通道 → Snackbar、计时由心跳按真实时间同步、`GameViewModel` → `GameStore` 落盘（每 10 秒节流）。
各层约束与状态机语义见 `docs/01`。

## 构建与运行

```powershell
.\gradlew.bat :composeApp:run                  # 桌面直接运行
.\gradlew.bat :composeApp:jvmTest --offline    # 单元测试 + UI 测试（53 项）
.\gradlew.bat :composeApp:createDistributable --offline   # 便携版（build/compose/binaries/main/app）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke-e2e.ps1   # 实机冒烟（置顶 + 固定坐标，自动截图）
.\gradlew.bat :composeApp:createDistributable  # 自带 JRE 的分发目录（binaries/main/app/SudokuInk）
.\gradlew.bat :composeApp:packageMsi           # Windows 安装包（首次需联网下载 WiX）
```

首次构建需联网拉取依赖，依赖缓存就绪后可加 `--offline`。仓库已含 Gradle wrapper，无需本机安装 Gradle；
启动 Gradle 的 `JAVA_HOME` 指向 JDK 17–24 即可（`jvmToolchain(21)` 会复用该 JVM，仓库内不写死任何本机路径）。
应用图标是**九宫格 + 数字**的纸墨风格（`5 / 3 / 4 / 9 / 7` 部分填、部分空），由 `tools/make-icon.ps1` 一次生成：
`composeApp/icons/` 供打包（Windows `.ico` / Linux `.png` / macOS `.icns`，写进 exe、MSI、DMG）与运行时窗口图标
（打进 jar，jpackage 不负责后者）；`assets/icon/` 输出桌面 / Android（含**自适应图标分层**）/ iOS / macOS / Web
各平台规格与矢量源文件。设计与规格见 `docs/06-应用图标设计.md`（含 Android 66dp 安全区规范与 `.icns` 生成流程）。

## 跨平台

| 目标 | 状态 | 说明 |
|---|---|---|
| Desktop / JVM | ✅ | Windows 上完成编译、测试、运行与 MSI 打包验证；三平台图标（`.ico` / `.png` / `.icns`）已就绪 |
| Android | 接入即用 | miuix 要求 `compileSdk 37`；步骤见 `docs/05` §3.1；自适应图标分层资源已生成（`docs/06` §6） |
| iOS / macOS | 接入即用 | miuix 已发布对应变体，构建需 macOS 主机；`.icns` 已生成（`docs/06` §7） |
| Web（wasmJs / js） | 接入即用 | miuix 已发布 Web 变体，可在 Windows 上构建 |

`core` 与 `state` 两层零平台依赖，新增平台只需补 target、entrypoint 与 `GameStore` 实现，业务代码不动。
平台策略与顺序见 `docs/05` §3。

## 文档索引

| 文档 | 内容 |
|---|---|
| `docs/01-架构设计.md` | 分层结构、依赖方向、数据流、状态机语义、平台约束 |
| `docs/02-设计规范.md` | 手写纸墨线视觉语言、令牌、墨线控件状态、页面布局、键盘与无障碍 |
| `docs/03-开发流程.md` | 环境准备、版本对齐、构建打包、目录约定、测试与验收 |
| `docs/04-AI辅助开发规范.md` | 上下文管理方法（四层上下文、@ 引用、分层投喂、负面约束） |
| `docs/05-发展规划.md` | 现状快照、架构演进、跨平台矩阵、功能分层、里程碑与执行清单 |
| `docs/06-应用图标设计.md` | 应用图标的设计说明、比例规范、细节层级与各平台产物清单 |
| `.codebuddy/rules/sudoku-dev.md` | 强制纪律：红线、开发思路、选型依据、模块说明、迭代计划 |

## 许可证

本项目以 [MIT 许可证](LICENSE) 开源。
