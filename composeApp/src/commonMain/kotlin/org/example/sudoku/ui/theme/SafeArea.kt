package org.example.sudoku.ui.theme

import androidx.compose.ui.Modifier

/**
 * **安全区内边距**（系统栏 / 刘海 / 导航栏）：各平台自己给。
 *
 * 为什么做成平台声明，而不是直接用标准 `WindowInsets.systemBars`：
 * - 当前 Compose 版本在已启用的 jvm 目标上**没有暴露任何 inset 常量**——实测
 *   `statusBars` / `navigationBars` / `systemBars` / `displayCutout` / `safeDrawing` / `ime`
 *   全部 unresolved，拿不到值；
 * - 桌面本来也不存在系统栏，原先第三方组件库 `Scaffold` 的 contentPadding 在桌面上同样是 **0**，
 *   所以这不是"降级"，只是把"桌面恒为 0"这件事显式化；
 * - 做成平台声明后，将来启用 Android / iOS 目标时**编译器会强制要求补 actual**，
 *   不会把刘海与导航栏的安全区悄悄漏掉。
 *
 * 各平台实现见 `SafeArea.jvm.kt`（桌面 = 恒等）。
 */
expect fun Modifier.safeAreaPadding(): Modifier
