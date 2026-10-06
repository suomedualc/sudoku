package org.example.sudoku

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.example.sudoku.platform.FileGameStore
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1180.dp, 900.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "数独 Sudoku",
    ) {
        // Compose Desktop 未提供最小尺寸 API，用底层 AWT 设置（与 docs/02-设计规范.md §7 一致）
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(940, 720)
        }
        // 桌面注入文件存档：实现首页「继续游戏」与关窗后恢复
        val store = remember { FileGameStore() }
        App(store = store, onExit = ::exitApplication)
    }
}
