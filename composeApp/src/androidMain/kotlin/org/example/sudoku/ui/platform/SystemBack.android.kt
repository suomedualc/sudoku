package org.example.sudoku.ui.platform

import androidx.activity.compose.BackHandler as ActivityBackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    ActivityBackHandler(enabled = enabled, onBack = onBack)
}
