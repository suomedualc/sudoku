# Android 端上线验证报告与 CheckList

> 版本：v1.0（2026-10-18）· 对象：`SudokuInk` Android 目标（`org.example.sudoku`）
> 范围：全量代码排查、bug 修复、兼容性核查、构建/测试验证、上线 checklist
> 关联：`docs/05-发展规划.md` · `docs/08-Android模拟器测试报告.md` · `docs/10-项目优化方案.md` · `_archive/codebuddy-rules-20261024/sudoku-review.md`

---

## 1. 落地推进时间表

| 阶段 | 内容 | 输出/验收 | 状态 |
|---|---|---|---|
| **S0 盘点与排查** | 通读 Android 平台接缝 + 并行全量审查 core/state/ui 三层 | bug 台账（本表 §3） | ✅ 已完成 |
| **S1 高危修复** | 修复崩溃/数据丢失/生命周期/兼容性 bug；minSdk 对齐 8.0+ | jvmTest 全绿 + assembleDebug 通过 | ✅ 已完成（本次） |
| **S2 打包与护栏** | 多实例存档互斥 ✅ · i18n 守卫 ✅ · A3 AAB 签名（待密钥） | 护栏已交付；AAB 待密钥决策 | 🔄 部分完成 |
| **S3 触屏/横屏体验** | 迭代 B：横屏双栏 ✅ + 双键盘笔记块 ✅ | 触屏单手步数 ≤2；横竖屏不丢状态 | ✅ 已交付 |
| **S4 真机矩阵** | A5：Android 8.0–15 全机型全系统冒烟 | 软渲染之外的真实性能基线 | ⏳ 需实体设备 |
| **S5 发布** | 商店素材 + 国内/海外渠道合规评估 | 上线 | ⏳ 决策点 |

> 时间表口径：S0/S1 为本会话已完成项；S2–S5 为后续推进项，S2 不依赖真机、可先行；S4 需 ≥1 台实体设备。

---

## 2. 本批次已完成修复

| # | 严重度 | 位置 | 问题 | 修复 |
|---|---|---|---|---|
| 1 | P1 | `androidMain/MainActivity.kt` | 生命周期判定 `event != ON_PAUSE`：切后台时 `ON_STOP` 紧随其后，把 `windowActive` 翻回 true → **挂后台仍走表** | 改为 `event == ON_RESUME`；顺带移除已废弃的 `LocalLifecycleOwner` |
| 2 | P1 | `composeApp/build.gradle.kts` | `minSdk=24` 但存档用 `java.nio.file.Files.move`（API 26 引入），Android 7.x 上 `NoClassDefFoundError` 被 `runCatching` 吞掉 → **存档静默失效** | `minSdk` 提升为 26（对齐"8.0+"承诺） |
| 3 | P2 | `ui/screens/MenuScreen.kt` | 难度抽屉光标上限 `difficulties.size + 1`（=5）但只渲染 4 项 → 键盘连按 ↓ 走进不可见第 5 项，高亮消失、Enter 只关抽屉不开局 | 上限改为 `difficulties.size` |
| 4 | P2 | `ui/screens/GameScreen.kt` | `boardDescription` 的 `remember` 键缺 `language` → 语言变化时读屏文案停旧语言 | key 补 `state.language` |
| 5 | P2 | `state/GameStore.kt` · `GameViewModel.kt` | `SavedGame` 不持久化 `hintUsed`/`hintsCount` → **续局后提示计数归零**，与盘面上已提示格不一致 | `SavedGame` + encode/decode + persist + toState 全链路补两字段（旧档缺行取默认） |
| 6 | P2 | `core/CellCursor.kt` | KDoc 写"停在第一个空格"与实现（可编辑格）相悖 | 修正注释 |

---

## 3. 全量排查 bug 台账

**已修（6 项，见 §2）**；以下是排查后判定**不修 / 挂账**的项及依据：

| # | 严重度 | 位置 | 问题 | 结论 |
|---|---|---|---|---|
| 7 | P2 | `state/GameReducer.kt`（Select/Digit） | 未校验 `pos ∈ 0..80` / `value ∈ 0..9` | **不修**：reducer 输入来自内部可信 UI（81 语义节点 / 数字键盘），生产路径不可能越界；补校验属过设计（违反"信任内部代码"） |
| 8 | P2 | `state/KeyMap.kt`（bind） | `bind` 本身未复检 1–9 拒绑 | **不修**：已由 UI 边界 `accepts` + `decode` 双处守住不变量 |
| 9 | P2 | `App.kt` 提示循环 | `LaunchedEffect(Unit)` 内 `stringsFor(state.language)` 已在循环内现取 | 已修（历史，见 review 规则 §5） |
| 10 | ✅ | 存档 | 多实例同时写 `save.txt` 无互斥 | 已实现（2026-10-18）：`SaveMutex` + `MutexGameStore` + `FileSaveMutex` |
| 11 | ✅ | `ui/i18n` | `Strings` 中英键一致性无自动守卫 | 已实现（2026-10-18）：`I18nGuardTest` 反射守卫，零依赖 |
| 12 | 挂账 | `MenuScreen` | 焦点模型与游标模型并存（Tab 聚焦后 Enter 走游标项） | 挂账 #14，需拍板 |

---

## 4. 兼容性核查结论（Android 8.0+ / API 26+）

| 项 | 结论 |
|---|---|
| 最低系统 | **minSdk 26 = Android 8.0**，满足"8.0 及以上"要求（targetSdk 36 / compileSdk 36） |
| 第三方依赖 | 仅 `androidx.activity:activity-compose` + `androidx.lifecycle:lifecycle-runtime-ktx`（均为 API 21+ 兼容），无 API>26 硬依赖 |
| `java.nio.file` | 已随 minSdk 26 对齐，无需改实现 |
| 边到边 | `enableEdgeToEdge()` + `WindowInsets.safeDrawing` + `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`（API 28+ 刘海/挖孔已避让；API 21+ 系统栏） |
| 字体 | 霞鹜文楷 + Nunito 经 `res/font` 打包（API 26+ 原生支持），数字族保留文楷回退链 |
| 返回键 | `SystemBackHandler`（activity-compose `BackHandler`）三端桥，语义正确 |
| 待验证 | **真机全机型矩阵**（8.0/9/10/11/12/13/14/15）——软渲染 AVD 已过 3 档，实体设备见 §7 A5 |

---

## 5. 测试与构建验证结果（本次）

| 命令 | 结果 |
|---|---|
| `.\gradlew.bat :composeApp:jvmTest --offline` | ✅ BUILD SUCCESSFUL（全绿；含新增续局提示计数断言） |
| `.\gradlew.bat :composeApp:assembleDebug --offline` | ✅ BUILD SUCCESSFUL（APK 21.2 MB，minSdk 26） |

> 备注：`quadraticBezierTo`、`runComposeUiTest` 等 deprecation 为既定技术债（非 bug），未在本批次处理。

---

## 6. 上线 CheckList

**准出前必须（Blocking）**
- [ ] **A3 AAB 打包**：`bundleRelease` + 签名密钥（密钥保管/轮换决策单独走升级流程），核对包名 `org.example.sudoku`、versionName `2.1.0` / versionCode `20100`、自适应图标
      （版本约定：`versionCode = major*10000 + minor*100 + patch`，改 versionName 必须同步改 versionCode，见 `build.gradle.kts` 注释）
- [ ] **A5 真机矩阵**：≥1 台实体设备过触感/性能清单，覆盖 Android 8.0 / 10 / 12 / 14 至少四档
- [x] **横屏双栏**（迭代 B）：棋盘左 + 控制栏右（`LandscapeControlWidth` 300dp），
      断点 `宽 > 高 且 高 < 480dp`（`GameLayoutTest` 3 例守着，含"1180×900 必须判竖屏"）。
      横屏不再给棋盘上下留浮动条槽，撤销/重做改挂左侧竖轨——实测 891×411dp 棋盘由
      **160dp（单格 17dp，撞 BoardMinSize 下限）提升到 237dp（27dp）**。
      `GameScreenUiTest.landscapeBoardKeepsCellsTappable` 断言单格 ≥24dp。
- [ ] **横屏真机验证**：以上为模拟器尺寸档的测试结论，仍需实体设备确认实际观感与比例
- [x] 多实例存档互斥（磁盘写冲突）：`SaveMutex` 端口 + `MutexGameStore` 装饰器；
      Android 端用**进程级单例**（旋转重建不得二次抢锁，否则静默只读）
- [ ] 商店素材：`play-store-512.png` + 截图 + 中英文商店描述

**建议（Non-blocking）**
- [x] i18n 键一致性守卫（机械化 `Strings` 中英双份校验）
- [x] 跨源集资产一致性守卫（`BundledAssetsParityTest`，SHA-256 逐字节比对字体/语料双份副本）
- [x] Android 端单元测试（`androidUnitTest` 源集，7 项覆盖 `AndroidGameStore` 原子写；
      纯 JVM 可跑，不需要 Robolectric）
- [ ] CI（**已移除**：曾配过 `.github/workflows/ci.yml`，因暂不验证 CI 流程而删除。
      将来需要时重建：`jvmTest` + `testDebugUnitTest` + 双平台编译即可）
- [ ] 字体子集化评估（Web 目标体积 13.3MB，已换官方 Lite 版；进一步子集需按 OFL 改名）
- [x] 触屏双键盘笔记块（B1）：主键盘下方一排**空心数字**，点它直接记笔记，不必先切「笔记模式」
      （触屏单手 3 次点击 → 1 次）。按纵向预算决定是否显示：极小屏（360×640）自动退回单键盘

---

## 7. 遗留待办与优先级（续 §1 时间表）

1. **A3 AAB 签名打包**（S2，S 规模）——唯一阻塞"可交付形态"的项，密钥决策需产品侧拍板。
2. ~~**多实例存档互斥**（S2）~~ ✅ 已交付；~~**i18n 守卫**~~ ✅ 已交付。
3. ~~**迭代 B 触屏体验**（S3，M 规模）~~ ✅ **已交付**：横屏双栏 + 双键盘笔记块，
   直接命中"流畅度/便捷性"体验指标。
4. **A5 真机矩阵**（S4）——S1/S2 完成后用实体设备收口兼容性承诺。