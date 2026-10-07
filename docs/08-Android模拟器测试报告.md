# Android 模拟器测试报告

> 编号：TR-2026-10-07-01 · 测试对象：`composeApp-debug.apk` 21.2 MB（versionName 2.1.0，minSdk 24 / targetSdk 36）
> 测试日期：2026-10-07 · 测试方式：自动化脚本（adb 键盘/触控注入 + uiautomator 语义断言 + 逐帧截图）+ 人工目检
> 关联：`docs/05` P0 Android 落地计划 · `.codebuddy/rules/sudoku-review.md` §5

## 1. 测试环境与设备矩阵

| 项 | 值 |
|---|---|
| 构建工具链 | Kotlin 2.4.0 · CMP 1.11.1 · AGP 8.10.1 · Gradle 8.12 · JDK 21 |
| 系统镜像 | `system-images;android-35;google_apis_playstore;x86_64`（三台复用同一镜像，差异仅设备规格） |
| 模拟器 | `emulator -no-snapshot -no-window -gpu swiftshader_indirect`（软渲染，性能数据偏保守） |
| 设备矩阵 | ① `Small_Phone_API_35`（小屏手机规格）② `Medium_Phone_API_35`（标准手机规格）③ `Pixel_Tablet_API_35`（平板规格） |
| 被测实现 | 本轮新增：`androidTarget()` + 6 个平台接缝 actual + `AndroidGameStore` + MainActivity 装配（业务代码零改动） |

## 2. 功能用例集与结果（三台设备逐台执行同一用例集）

| 用例 | 验证内容 | Small | Medium | Tablet | 说明 |
|---|---|---|---|---|---|
| BOOT / INSTALL | 模拟器启动、APK 安装 | PASS | PASS | PASS | |
| T1 菜单渲染 | 五入口 + **语料库句子**（离线本地数据库） | PASS | PASS | PASS | |
| T2 开局 | 开始游戏 → 难度抽屉 → 简单 → 棋盘页 | PASS | PASS | PASS | |
| T3 触摸选格 | 点空格 → 语义"已选中"；**手指不弹悬浮面板**（策略） | PASS | PASS | PASS | 首轮 FAIL 为脚本断言乱码，见 §5 |
| T4 填数 | 点数字键 → 格子填入 | **FAIL** | **FAIL** | **FAIL** | **窄屏适配缺陷**，见 §4-D1 |
| T5 擦除 | 擦除键清除 | PASS | PASS | PASS | |
| T6 提示 | 提示图标 → 填入正确答案 + 计数 | **FAIL** | **FAIL** | **FAIL** | 同 D1 连锁（顶栏/键盘挤压区域命中失败） |
| T7 暂停/继续 | 白纸遮题 + 继续 | PASS | PASS | PASS | |
| T8 后台暂停 | Home → 返回 → "已暂停"遮罩在场 | PASS | PASS | PASS | 生命周期 → isWindowActive → 自动停表 |
| T9 杀进程恢复 | force-stop → 重启 → 继续游戏 → 盘面还原 | PASS | PASS | PASS | 存档原子写 + 启动恢复链路 |
| T10 夜墨切换 | 明暗切换 | PASS | PASS | PASS | 夜墨渲染目检通过（深底浅墨、语料正常） |
| T11 返回键 | 系统返回行为 | INFO | INFO | INFO | v1：返回 = 退出（对局自动存盘），导航语义待 P3 迭代 |
| 性能 | 冷启动 / 内存 | 3.24s / 100 MB | 3.22s / 100 MB | 3.24s / 100 MB | 软渲染模拟器数据，真机预计显著更好 |

**通过率**：12/14 用例通过（T4/T6 失败同源，见 §4-D1）；三台设备结果**完全一致**（一致性说明缺陷是系统性的布局问题而非环境偶发）。

## 3. 通过项的关键证据（截图，`build/android-test/<设备>/`）

- `s1_menu.png`：五入口 + 语料库句子（**离线语料服务**验证）；
- `s2_game_started.png` / `s10_night.png`：棋盘墨线、题面字框、宫线、**候选灰字**、夜墨反色、浮动条禁用态全部正确；
- `s7_paused.png` / `s8_background_return.png`：暂停遮题 + 后台自动暂停；
- `s9_restored.png`：杀进程后盘面还原；
- `diag/d1_after_cell_tap.png`：夜墨下首页渲染（诊断轮次副产物）。

## 4. 发现的缺陷

### D1（P1，本轮最重要发现）：窄屏下顶栏与功能区布局失效
**现象**（三台设备一致，标准手机 ~411dp 即可复现，截图 `s2_game_started.png` / `s4_after_digit.png`）：
1. **数字键盘挤压**：1–9 键被压成 ~19dp 细条，角标与键面数字重叠、点击命中失败（T4/T6 直接原因）；
2. **开关行溢出**：笔记/显示/候选/严格四项的勾选框被裁出屏幕；
3. **顶栏读数截断**：难度后计时/进度不可见；
4. **底部键位提示行折行截断**。

**根因**：对局页"棋盘 — 功能区同宽"的中轴设计在桌面上成立（棋盘 ≥500dp），窄屏上棋盘缩到 ~330dp 后，与棋盘同宽的 `NumberRow`/`ToggleRow` 空间不足（`EraseKeyWidth 72dp` 固定占用进一步挤压数字键）。

**修复建议（P1，下一迭代）**：
- 窄屏断点（棋盘宽 < ~400dp）：数字键改用**整页宽**或折行两排（1–5 / 6–9），保证键宽 ≥ 40dp；
- 开关行窄屏 2×2；顶栏窄屏隐藏进度读数或减档；键位提示行 `maxLines = 2`；
- 回归：`InkThemeTest`/`BoardCanvasUiTest` 不受影响，新增窄屏布局的 UI 测试 + 冒烟 `-Flow ux` 补窄屏档。

### D2（P2）：返回键导航语义未实现
系统返回 = 退出 Activity（对局自动存盘不丢数据，T11 验证 pid 存活/存档正常），但"对局页返回 → 首页"的导航语义待 P3 迭代（`BackHandler` 需要读取屏幕状态的桥接）。

### D3（P3）：应用图标未接线
`androidMain` 未配置自适应图标（`docs/06` 资源已生成、未复制进 `res/`），启动器显示默认图标。

## 5. 测试基建的教训（沉淀进脚本注释）

1. `adb exec-out screencap -p >` 在 PowerShell 文本管道下损坏 PNG → 必须 `screencap -p /sdcard/` + `adb pull`；
2. 方向键 PostMessage 必须**带 EXTENDED 标志**（漏了=静默丢弃，"脚本按了、应用没反应"）；
3. PS 5.1 无 BOM 脚本按 ANSI 解析 → **脚本保持 ASCII**，中文匹配串放 UTF-8 数据文件显式读取（T3 首轮误报根因）；
4. 多行 `@('k=' + $v, ...)` 数组字面量在 PS 5.1 下不拼接 → 种子存档行用 `+= ('k=' + $v)`；
5. uiautomator dump 可能返回陈旧快照 → 断言前双 dump、并配合截图目检。

## 6. 结论与放行意见

- **功能正确性**：除窄屏布局挤压连锁外，全部核心流程（开局/选格/暂停/恢复/杀进程还原/夜墨/离线语料/生命周期停表）在三台设备上验证通过；
- **兼容性**：三档屏幕规格行为一致；软渲染模拟器冷启动 3.2s、内存 ~100 MB（合理）；
- **放行意见**：Android 构建链路与业务移植**放行**；真机测试与窄屏适配修复（D1）列为下一迭代的准入条件，随后方可进入 AAB 打包（原计划阶段 5）。

## 7. 修复轮（2026-10-07 同日）：D1 已修复并复验

**修复内容**（窄屏断点 `DesignTokens.Sizes.FunctionNarrowMax = 460dp`，由 GameScreen 按页宽判定）：
1. **九宫格键盘**：`NumberPad(grid = true)`——3×3 数字 + 右侧**通高擦除键**，键宽恢复 ~42dp；
   **桌面/平板（≥460dp）保持横排 1–9 不变**（横排是宽屏既定设计）；
2. **「游戏设置」抽屉**：窄屏下四项偏好从常驻开关行收进 TopDrawer（覆盖层唯一形态），
   常驻空间只留一个"游戏设置"按钮；宽屏保持常驻开关行；
3. **顶栏减档**：窄屏只保留难度 + 计时两个主读数（提示计数 / 进度在整盘读屏文案中仍可获取）；
4. **键位提示行** `maxLines = 2`（预留高度按两行计），折行不再截断。

**复验结果**（Medium_Phone 411dp 竖屏，截图 `build/android-test/medium_narrow_verify2.png` / `medium_settings_drawer.png`）：
- 九宫格键宽 ~42dp、角标清晰、竖置擦除键拇指可达 ✓；
- 顶栏"简单 00:01"无截断 ✓；键位提示两行完整 ✓；
- 「游戏设置」抽屉：四开关整宽、勾选框完整、层级清晰 ✓；
- 桌面回归 jvmTest 137/137 全绿（宽屏布局路径未变）✓。

**剩余**：真机测试与 AAB 打包（原计划阶段 5）、返回键导航语义（D2）、自适应图标接线（D3）。
