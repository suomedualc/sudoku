package org.example.sudoku.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * 安全区（系统栏 / 刘海 / 导航栏 / 输入法）：配合 MainActivity 的 `enableEdgeToEdge()` 与
 * `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`，内容铺满全屏后用 `safeDrawing` insets
 * 把内容压回可见区域。
 *
 * `safeDrawing` = `systemBars ∪ displayCutout ∪ ime`：比单独的 `systemBars` 多挡一层
 * **刘海 / 挖孔屏**（横屏时缺口常落在内容区）。本作无文本输入，`ime` 恒为 0，
 * 因此此处等价于"系统栏 + 刘海"。
 *
 * expect 签名是**普通函数**（桌面端恒等、无组合上下文），而 `WindowInsets.safeDrawing`
 * 是 @Composable 属性，因此这里用 `composed {}` 延迟到组合期取 insets。
 */
actual fun Modifier.safeAreaPadding(): Modifier = composed {
    windowInsetsPadding(WindowInsets.safeDrawing)
}