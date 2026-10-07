package org.example.sudoku.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint
import org.example.sudoku.state.KeyTokens

/**
 * 按键事件 → 键位**令牌**（令牌的定义见 `state/KeyMap.kt`）。
 *
 * 这是全工程**唯一**把 Compose 的 [Key] 翻译成业务数据的地方：
 * 状态机与存档只认字符串令牌，因此键位设置可单测、可落盘、换平台不失效。
 *
 * 绑不了的键（修饰键 / F1–F12 / Tab 等）返回 `null`，界面会提示"此键不支持"——
 * 与其绑上一个"看着能用、按下没反应"的键，不如当场说清。
 */
object KeyToken {
    fun of(key: Key, utf16CodePoint: Int): String? = when (key) {
        Key.DirectionUp -> KeyTokens.UP
        Key.DirectionDown -> KeyTokens.DOWN
        Key.DirectionLeft -> KeyTokens.LEFT
        Key.DirectionRight -> KeyTokens.RIGHT
        Key.Spacebar -> KeyTokens.SPACE
        Key.Backspace -> KeyTokens.BACKSPACE
        Key.Delete -> KeyTokens.DELETE
        else -> {
            // 其余交给"打出来的字符"：字母与数字可绑（统一大写，避免大小写两份配置）
            val ch = utf16CodePoint.toChar()
            if (ch.isLetterOrDigit()) ch.uppercaseChar().toString() else null
        }
    }

    fun of(event: KeyEvent): String? = of(event.key, event.utf16CodePoint)
}
