package org.example.sudoku.ui.platform

import androidx.compose.runtime.Composable

/** 桌面没有系统返回键（Esc 语义已由 TopDrawerKeys / 棋盘快捷键覆盖），空实现。 */
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no-op：签名与 expect 保持一致
}
