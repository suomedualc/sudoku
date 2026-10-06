# 数独 Sudoku · 手写纸 · 简约油墨（Kotlin / Compose Multiplatform）

单机数独应用：一份 Kotlin 代码，`core`（算法）/ `state`（状态机）两层零平台依赖，
界面采用**手写纸 · 简约油墨**视觉语言（只有纸与墨两色、手绘线条、大量留白），
页面骨架与提示通道由 [miuix](https://github.com/compose-miuix-ui/miuix) 提供。

| 目标平台 | 状态 |
|---|---|
| **Desktop（JVM）** | ✅ 已启用并验证（Windows 实测编译 / 测试 / 运行；窗口 1180×900；首页 → 难度 → 对局 → 冲突反馈 → 重启续局 全链路已截图验收） |
| Android | ⏸ 未启用：miuix 要求 `compileSdk 37`，本机 SDK 只装到 36（官方仓库已发布该平台，装齐即可启用，见"重要说明 2"） |
| iOS / macOS / Web | ⏸ 未声明 target：miuix 已发布 `iosarm64 / iossimulatorarm64 / macosarm64 / js / wasm-js` 变体，接入即用（详见 `docs/05-发展规划.md` §3） |

## 视觉风格（手写纸 · 简约油墨）

- **只有纸与墨**：纸 `#F7F5F0`、墨 `#1B1A17`（+ 次级 / 最淡两级）。**没有任何色相**——
  冲突用「斜向排线 + 手绘圈」，禁用用「虚线框」，可填性用「线重」，模式用「线型」。
- **手绘而非几何**：所有边框、网格、勾选、排线都通过 `inkLine / inkRoundRect / inkHatch` 绘制
  （折线微弯 + 叠一遍淡墨）。抖动由**纯函数种子**生成，重绘时线条完全静止、不闪动。
- **手写体**：引用本机已安装的中文手写字体（方正硬笔楷书 → 霞鹜文楷 → 楷体 → 华文楷体 → …），
  取不到时回退衬线体；**字体不随包分发**（避免体积与再分发授权问题）。
- **留白优先**：间距节奏比 Material 更疏（16 / 24 / 32 / 48 dp），不靠装饰块分区。
- 主题**固定浅色纸面**（不跟随系统深色）；"夜墨"反色主题列入路线图（`docs/05` §4 Tier 2）。

## 功能

- **首页仅三个入口**：`开始游戏`（弹出墨框难度面板：简单 40 / 普通 46 / 困难 52 / 大师 56 空）、
  `继续游戏`（无存档时虚线框置灰并附说明）、`退出游戏`（二次确认，并提示会自动保存）。
  **不含对弈、联网与社交**——底部仅有"单机 · 无需联网"一行淡墨小字与一张手绘棋盘草图。
- **棋盘（墨线自绘）**：9×9 手绘网格、宫线加粗、选中双框、同行列宫淡墨、同值淡墨、
  **冲突格斜排线 + 冲突数字手绘圈**；给定数字实墨、玩家填入淡墨、候选数小号淡墨。
- **数字输入**：点格子选中 → 点数字键填入；`擦除` 清空；笔记模式记候选数（落子自动清理同行列宫候选）。
- **难度与唯一解**：生成时校验唯一解（空格数是目标上限，实测大师档约 22/25 次挖满，见"重要说明 6"）。
- **计时**：UI 侧心跳每秒累加，暂停与通关时停止。
- **错误提示**：改给定格、在已填格记笔记、数字冲突、通关均通过 Snackbar（纸面上的"墨块"）反馈。
- **撤销 / 重做 / 提示 / 重置**：完整对局辅助（撤销栈上限 300 步；提示次数显示在状态行）。
- **自动存档与续局**：有未完成对局时自动落盘（桌面 `~/.sudoku-ink/save.txt`），
  重启后首页可「继续游戏」，通关 / 重置到新局会清档。
- **严格模式 / 显示笔记**：手绘勾选框（整行可点，带 `Role.Switch` 语义）。
- **暂停遮挡**：暂停时用白纸盖住题面（不泄题），`Esc` / 空格 可继续。
- **桌面键盘**：`1–9` 填数、`0`/`Delete` 擦除、方向键选格（自动跳过给定格）、`N` 笔记、
  `H` 提示、`P`/空格 暂停、`Ctrl+Z` 撤销、`Ctrl+Y` 重做。
- **自适应布局**：宽屏「棋盘左 + 控制右」双栏（棋盘顶部对齐并为提示条预留 64dp），
  窄屏（手机竖屏 / 小窗口）纵向可滚动，数字键盘不会被挤出可视区。

## 技术栈与版本（三者强绑定，不可单独升降级）

| 组件 | 版本 | 说明 |
|---|---|---|
| Kotlin | 2.4.0 | 由 Gradle 插件拉取 |
| Compose Multiplatform | 1.11.1 | miuix 0.9.2 直接依赖该版本 |
| miuix | 0.9.2 | 提供主题 / 骨架 / 提示通道（见下节） |
| Gradle | 8.12 | wrapper 已入库，无需本机安装 |
| JDK | 21（17–24 皆可） | **启动 Gradle 的 `JAVA_HOME` 必须是 17–24**，JDK 25 会直接失败 |

## miuix 的职责边界与自绘组件

改版后 miuix 只负责"骨架 + 主题 + 提示"，视觉组件全部自绘（原因见 `docs/02-设计规范.md` §3.2）：

| 用途 | 由谁提供 |
|---|---|
| 主题上下文 | `MiuixTheme(colors = lightColorScheme())`（固定浅色纸面） |
| 页面骨架 / 系统栏安全区 | `Scaffold`（`App.kt` 完整应用 `PaddingValues`） |
| 一次性提示 | `SnackbarHostState.showSnackbar(message, SnackbarDuration.Short)` + `SnackbarHost` |
| 按钮 / 数字键 | 自绘 `InkButton` / `InkKey`（共用 `InkSurface` 的五态反馈） |
| 开关 | 自绘 `InkToggleRow`（手绘勾选框，整行可点） |
| 分组容器 / 遮罩 | 自绘 `InkPanel` / `InkOverlay` |
| 标题 / 分隔 / 装饰 | 自绘 `InkTitleFrame` / `InkDivider` / `InkGridSketch` |
| 棋盘 | 自绘 `BoardCanvas`（墨线 + 墨字，见 `docs/02` §5） |

## 架构（详见 `docs/01-架构设计.md`）

```
org.example.sudoku
├── core/         领域内核：位掩码 + MRV 求解、出题、冲突、候选、时长格式化（零平台依赖）
├── state/        应用层：GameState / GameAction / GameReducer（纯状态机）
│                 + GameViewModel（状态容器）· GameStore / SaveCodec（存档端口与编解码）
├── ui/
│   ├── theme/    Ink.kt（纸墨配色 + 手写体 + 手绘原语）· DesignTokens.kt（尺寸）
│   ├── components/  InkWidgets.kt（墨线控件库）· BoardCanvas.kt · NumberPad.kt
│   ├── screens/  MenuScreen（三入口 + 难度 + 退出确认）· GameScreen（双形态 + 键盘）
│   └── App.kt    MiuixTheme + Scaffold + 两页导航 + Snackbar（无顶部栏）
└── entrypoints
    ├── androidMain/MainActivity.kt          （未参与编译，待启用）
    └── jvmMain/  main.kt（窗口 1180×900 / 最小 940×720）· platform/FileGameStore.kt · ui/theme/InkFonts.jvm.kt
```

单向数据流：`输入（点击 / 键盘 / 心跳）→ GameAction → GameReducer.reduce → GameState → Compose 重组`；
副作用只有两处：`state.message` → Snackbar，`GameViewModel` → `GameStore` 落盘（`Tick` 每 10 秒节流一次）。

## 目录

```
sudoku/
├── settings.gradle.kts · build.gradle.kts · gradle.properties
├── gradle/libs.versions.toml       版本锁（Kotlin / CMP / miuix）
├── .codebuddy/rules/sudoku-dev.md  开发纪律（红线 + 设计说明 + 迭代计划）
├── docs/                           01 架构 · 02 设计规范 · 03 流程 · 04 AI 协作 · 05 规划
├── composeApp/
│   ├── build.gradle.kts            jvmToolchain(21) + jvm() target + compose.desktop 配置
│   └── src/
│       ├── commonMain/…            业务代码（core / state / ui / App.kt）
│       ├── commonTest/…            单测（core 4 + state 12 + 存档 6 = 22 项）
│       ├── androidMain/…           MainActivity.kt · AndroidManifest.xml（未启用）
│       └── jvmMain/…               桌面窗口 · 文件存档 · 手写字体探测
├── archive/                        旧版 TS / Rust 实现归档（只读参考，见"重要说明 7"）
└── README.md
```

## 构建与运行

前置：**启动 Gradle 的 `JAVA_HOME` 必须是 JDK 17–24（本项目用 21）**——实测 JDK 25 会让 Gradle 8.12
直接失败，且报错只有一串版本号、极易误判为工程问题；本机 JDK 21 位于
`D:\env\_SDK\versions\jdk_versions\jdk-21.0.12.0_10`（`gradle.properties` 已声明该路径供 `jvmToolchain(21)` 解析）。
首次构建需联网拉取依赖（体积较大），依赖缓存就绪后可加 `--offline`。
仓库已含 Gradle wrapper（8.12），无需本机安装 Gradle。

```powershell
$env:JAVA_HOME="D:\env\_SDK\versions\jdk_versions\jdk-21.0.12.0_10"   # 必须

.\gradlew.bat :composeApp:run                                 # 桌面直接运行
.\gradlew.bat :composeApp:jvmTest --offline                   # 单元测试（当前 22 项）
.\gradlew.bat :composeApp:createDistributable                 # 生成自带 JRE 的可分发目录
.\gradlew.bat :composeApp:packageDistributionForCurrentOS     # msi / dmg / deb（需联网下载 WiX）
```

（Linux/macOS 用 `./gradlew`。Android 启用方式见"重要说明 2"。）

## 文档索引

| 文档 | 内容 | 何时看 |
|---|---|---|
| `docs/01-架构设计.md` | 分层结构、依赖方向、数据流、状态机语义、与 TS 版关系、已知约束 | 改结构 / 加平台前 |
| `docs/02-设计规范.md` | 手写纸墨线视觉语言、令牌、墨线控件五态、首页/对局页布局、棋盘墨线表达、无障碍 | 改 UI / 配色 / 交互前 |
| `docs/03-开发流程.md` | 环境准备、版本对齐、构建运行、目录约定、测试、贡献扩展、已知风险 | 第一次构建 / 排错 |
| `docs/04-AI辅助开发规范.md` | 上下文管理方法（四层上下文、@ 引用、分层投喂、负面约束、锚点模板） | 每次开新会话 |
| `docs/05-发展规划.md` | 现状快照、架构演进阶段、跨平台矩阵与策略、功能扩展分层、里程碑与验收标准 | 决定"下一步做什么"前 |
| `.codebuddy/rules/sudoku-dev.md` | 强制纪律：红线、开发思路、技术选型依据、模块设计说明、迭代计划 | 自动加载，改代码全程生效 |

## 重要说明

1. **版本锁定（重要）**：`gradle/libs.versions.toml` 锁定 **Kotlin 2.4.0 / Compose Multiplatform 1.11.1 /
   miuix 0.9.2**。miuix 0.9.2 的 klib 元数据由 Kotlin 2.4.0 编译，且直接依赖 CMP 1.11.1——
   降级 Kotlin/CMP 会导致"Incompatible classes were found in dependencies"（元数据不兼容）编译失败。
   Maven Central 上已有 miuix 0.9.3 / 0.9.4，但同样要求 `minCompileSdk 37`，升级不解决 Android 问题。
2. **Android 目标未启用**：miuix 的 Android 产物要求 `compileSdk 37`（已用 `miuix-ui.aar` 里的
   `aar-metadata.properties` 核实，0.9.2/0.9.3/0.9.4 都要求 37）。截至 2026-10-06，`platforms;android-37`
   与 `build-tools;37.0.0` **已在 Google 官方 SDK 仓库发布**，本机 `D:\env\Android-SDK` 目前只装到
   android-36，因此仍无法编译。启用步骤：
   ① `sdkmanager "platforms;android-37" "build-tools;37.0.0"`；
   ② 在 `composeApp/build.gradle.kts` 增加 AGP 插件与 `androidTarget()`（namespace / compileSdk=37 / minSdk）；
   ③ 给 `androidMain` 加 `androidx.activity:activity-compose` 依赖（`MainActivity` 用到 `setContent` / `enableEdgeToEdge`）。
   `androidMain`（MainActivity / Manifest）已保留在磁盘，Manifest 的 AppCompat 主题也已换成系统内置主题。
3. **构建与验收已验证**（2026-10-06，Windows）：`compileKotlinJvm` 通过；`jvmTest` **22 项全绿**
   （内核 4 + 状态机 12 + 存档 6）；`createDistributable` 通过；桌面实例完成
   「首页 → 难度选择 → 对局 → 落子/冲突反馈 → 关窗重启 → 继续游戏」的截图验收。
4. **字体**：手写体来自**本机系统字体**（不打包分发），探测失败自动回退衬线体；
   若要在多端统一字形，改为打包字体资源（`docs/02` §2.3 / `docs/05` §2 阶段 C）。
5. **配色**：只用纸与墨两级色阶，禁止色相；冲突 / 禁用 / 可填性 / 模式分别用排线、虚线、线重、线型表达
   （`docs/02` §1、§5）。主题固定浅色纸面。
6. **已知限制**（与 `docs/05` §1 一致）：出题空格数是上限（大师档实测 22/25 次挖满）；
   计时为每秒累加、长局有漂移且窗口最小化仍走表；存档只保存对局（不保存玩法开关）；
   棋盘尚无逐格无障碍语义；首页三入口暂无键盘上下选择。
7. **归档目录**：旧版 TypeScript 实现（`archive/legacy/` 展开源码 + `typescript-v2-20261006.zip`）与
   Rust v1 实现（`sudoku-rust-v1-20261006.zip`）只作历史参考，**不是**当前实现，请勿引用。
   `archive/**/node_modules` 与 `archive/**/dist` 已被 `.gitignore` 忽略。
