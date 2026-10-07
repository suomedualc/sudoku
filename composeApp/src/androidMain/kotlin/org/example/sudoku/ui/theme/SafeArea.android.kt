package org.example.sudoku.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * 安全区（系统栏 / 刘海 / 导航栏）：配合 MainActivity 的 `enableEdgeToEdge()`，
 * 内容铺满全屏后用 `systemBars` insets 把内容压回可见区域。
 *
 * expect 签名是**普通函数**（桌面端恒等、无组合上下文），而 `WindowInsets.systemBars`
 * 是 @Composable 属性，因此这里用 `composed {}` 延迟到组合期取 insets。
 */
actual fun Modifier.safeAreaPadding(): Modifier = composed {
    windowInsetsPadding(WindowInsets.systemBars)
}
