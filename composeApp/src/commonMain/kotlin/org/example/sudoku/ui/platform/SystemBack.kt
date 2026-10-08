package org.example.sudoku.ui.platform

import androidx.compose.runtime.Composable

/**
 * 系统返回键桥（Android 预见式返回）。桌面没有"系统返回键"概念，actual 为空实现；
 * Android actual 直通 `androidx.activity.compose.BackHandler`。
 *
 * [enabled] 传 `false` 时不拦截（例如首页：返回键走系统默认行为 = 退出应用）。
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean = true, onBack: () -> Unit)
