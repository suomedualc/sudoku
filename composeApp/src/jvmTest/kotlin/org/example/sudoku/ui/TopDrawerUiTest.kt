package org.example.sudoku.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import org.example.sudoku.core.Difficulty
import org.example.sudoku.ui.screens.MenuScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 顶部抽屉的**键盘路由**端到端验证（真实组合 + 真实按键注入）。
 *
 * 这类问题只有在组合里才能验：`onPreviewKeyEvent` 父先于子、抽屉是否抢焦点、
 * `Esc` 该归谁——单测只能覆盖纯策略（见 `TopDrawerKeysTest`），这里覆盖装配。
 */
@OptIn(ExperimentalTestApi::class)
class TopDrawerUiTest {

    @Test
    fun enterOpensDifficultyDrawerAndEnterStartsSelectedDifficulty() = runComposeUiTest {
        var started: Difficulty? = null
        setContent {
            MenuScreen(
                canResume = false,
                onStart = { started = it },
                onResume = {},
                onExit = {},
            )
        }

        onNodeWithText("开始游戏").requestFocus()
        onNodeWithText("开始游戏").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()   // 等抽屉滑入动画落定（否则节点尚未进入可视区）
        onNodeWithText("选择难度").assertIsDisplayed()

        // 抽屉内 ↓↓ → 第三项「困难」
        onNodeWithText("选择难度").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        waitForIdle()
        assertEquals(Difficulty.Hard, started, "抽屉内的方向键应移动高亮，Enter 应按高亮项开局")
    }

    @Test
    fun unhandledKeysAreSwallowedWhileDrawerIsOpen() = runComposeUiTest {
        var started: Difficulty? = null
        var resumed = false
        setContent {
            MenuScreen(
                canResume = false,
                onStart = { started = it },
                onResume = { resumed = true },
                onExit = {},
            )
        }

        onNodeWithText("开始游戏").requestFocus()
        onNodeWithText("开始游戏").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithText("选择难度").assertIsDisplayed()

        // 抽屉是模态的：数字键、方向键未处理时都不该穿透到下面的菜单
        onNodeWithText("选择难度").performKeyInput {
            pressKey(Key.Five)
            pressKey(Key.NumPad5)
        }
        waitForIdle()
        onNodeWithText("选择难度").assertIsDisplayed()
        assertNull(started, "抽屉打开时未处理的键不该触发开局")
        assertEquals(false, resumed)
    }

    @Test
    fun escapeClosesDrawerWithoutActivatingAnything() = runComposeUiTest {
        var started: Difficulty? = null
        var exit = false
        setContent {
            MenuScreen(
                canResume = false,
                onStart = { started = it },
                onResume = {},
                onExit = { exit = true },
            )
        }

        onNodeWithText("开始游戏").requestFocus()
        onNodeWithText("开始游戏").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithText("选择难度").assertIsDisplayed()

        onNodeWithText("选择难度").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertNull(started, "Esc 只关抽屉，不应顺带开局")
        assertEquals(false, exit)
    }

    @Test
    fun exitConfirmRequiresExplicitConfirmation() = runComposeUiTest {
        var exit = false
        setContent {
            MenuScreen(
                canResume = false,
                onStart = {},
                onResume = {},
                onExit = { exit = true },
            )
        }

        // 主菜单：↓ 一次即到「退出游戏」（「继续游戏」无存档时置灰，会被跳过；再按一次 ↓ 会绕回「开始游戏」）
        onNodeWithText("开始游戏").requestFocus()
        onNodeWithText("开始游戏").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        waitForIdle()
        onNodeWithText("退出游戏？").assertIsDisplayed()

        // 默认高亮在「取消」：Enter 不应退出
        onNodeWithText("退出游戏？").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(false, exit, "默认高亮是「取消」，Enter 不能直接退出")

        // 再开一次（主菜单高亮仍停在「退出游戏」，直接 Enter 即可），↓ 到「退出」再 Enter → 才真的退出
        onNodeWithText("开始游戏").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithText("退出游戏？").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        waitForIdle()
        assertEquals(true, exit, "高亮移到「退出」后 Enter 应触发退出")
    }
}
