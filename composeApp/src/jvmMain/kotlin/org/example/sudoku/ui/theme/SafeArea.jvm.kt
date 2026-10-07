package org.example.sudoku.ui.theme

import androidx.compose.ui.Modifier

/**
 * 桌面没有系统栏 / 刘海 / 导航栏 → 安全区内边距恒为 0（恒等，不加任何 padding）。
 *
 * 启用 Android / iOS 目标时必须补各自的 actual：`WindowInsets` 的 inset 常量在
 * 非 Android 目标上不可用，但在 Android 上可用（届时按 `systemBars ∪ displayCutout` 取）。
 */
actual fun Modifier.safeAreaPadding(): Modifier = this
