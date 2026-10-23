package org.example.sudoku.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import org.example.sudoku.core.Difficulty
import org.example.sudoku.state.AppLanguage
import org.example.sudoku.state.GameStats
import org.example.sudoku.ui.screens.MenuScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 首页菜单的**键盘导航**行为测试。
 *
 * 为什么单独一个文件：`MenuScreen` 此前只在 `TopDrawerUiTest` 里被**当容器用**
 * （为了测抽屉的键盘路由），它自身的菜单导航——方向键移动、跳过置灰项、
 * 抽屉内光标上限——一直没有测试。2026-10-24 修过一个正是这类缺陷：
 * 难度抽屉的光标上限写成 `difficulties.size + 1`（抽屉里没有「取消」项），
 * 连按 ↓ 会走到不存在的第 5 项，高亮消失、Enter 只关抽屉不开局。
 *
 * 这里守三件事：
 * 1. 难度抽屉光标**不会越界**（连按远超条目数的 ↓ 之后仍能正常开局）；
 * 2. 「继续游戏」置灰时方向键**跳过**它；
 * 3. 主菜单方向键循环 + Enter 激活当前项。
 */
@OptIn(ExperimentalTestApi::class)
class MenuScreenUiTest {

    private fun ComposeUiTest.hostMenu(
        canResume: Boolean = false,
        onStart: (Difficulty) -> Unit = {},
        onResume: () -> Unit = {},
        onExit: () -> Unit = {},
    ) {
        setContent {
            MenuScreen(
                canResume = canResume,
                stats = GameStats(),
                language = AppLanguage.ZhCn,
                onLanguageChange = {},
                onStart = onStart,
                onResume = onResume,
                onExit = onExit,
                darkMode = false,
                onToggleDark = {},
            )
        }
    }

    /**
     * 回归：**难度抽屉的光标上限必须等于条目数**。
     *
     * 修复前的表现：`difficultyItemCount = difficulties.size + 1`（抽屉里其实没有「取消」项）。
     * 从第 1 项连按 **4 次** ↓ —— 4 项时恰好绕回第 1 项，而越界实现会停在 **第 5 项（不存在）**：
     * 没有任何按钮高亮，按 Enter 走 `index < difficulties.size` 判定为 false，
     * 于是**只关抽屉、不开局**，玩家觉得"卡住了"。
     *
     * 按 4 次是刻意的：若按 5 次，越界实现会绕回 0 而"看起来正常"，测试就抓不到这个 bug
     * （已实测验证：7 次时两种实现都通过，4 次才能区分）。
     */
    @Test
    fun difficultyCursorDoesNotOverrunItemCount() = runComposeUiTest {
        var started: Difficulty? = null
        hostMenu(onStart = { started = it })

        onNodeWithText("开始游戏").requestFocus()
        onNodeWithText("开始游戏").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithText("选择难度").assertIsDisplayed()

        // 4 次 ↓：正确实现绕回第 1 项，越界实现停在不存在的位置
        repeat(Difficulty.entries.size) {
            onNodeWithText("选择难度").performKeyInput { pressKey(Key.DirectionDown) }
        }
        onNodeWithText("选择难度").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(started != null, "连按 4 次 ↓ 后按 Enter 仍应开出一局（光标不得越界到不存在的项）")
        assertEquals(
            Difficulty.entries.first(),
            started,
            "4 项连按 4 次 ↓ 应绕回第 1 项；若停在别处说明上限算错",
        )
    }

    /**
     * 难度抽屉：**↑ 从第一项往上绕回到最后一项**（光标是循环的）。
     *
     * 这是"循环"这一设计选择的直接验收：4 项、起始 0，按一次 ↑ 应落在第 4 项（Expert）。
     * 顺带证明 `move` 用的是 `(next + delta + size) % size` 而不是简单加减。
     */
    @Test
    fun difficultyCursorWrapsUpwardToLastItem() = runComposeUiTest {
        var started: Difficulty? = null
        hostMenu(onStart = { started = it })

        onNodeWithText("开始游戏").requestFocus()
        onNodeWithText("开始游戏").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        onNodeWithText("选择难度").performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithText("选择难度").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(Difficulty.entries.last(), started, "从第一项按 ↑ 应绕到最后一项")
    }

    /**
     * 「继续游戏」置灰时，方向键必须**跳过**它。
     *
     * `mainEnabled` 把置灰项排除在可停靠集合之外。若跳过逻辑失效，光标会停在置灰项上，
     * 此时按 Enter 什么也不会发生（`activateMain` 里 `if (canResume)` 为 false）——
     * 玩家看到"高亮在继续游戏上，按 Enter 没反应"。
     *
     * 断言：无存档时，从「开始游戏」按一次 ↓ 应直接落到**下一可用项**（「查看游玩统计」），
     * Enter 随即打开统计抽屉。这条同时证明了"跳过"与"Enter 走光标"两件事。
     */
    @Test
    fun disabledResumeIsSkippedByArrowKeys() = runComposeUiTest {
        var resumed = false
        hostMenu(canResume = false, onResume = { resumed = true })

        onNodeWithText("开始游戏").requestFocus()
        onNodeWithText("开始游戏").performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText("开始游戏").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(!resumed, "无存档时不应触发继续游戏")
        // 跳过置灰的「继续游戏」后落在「查看游玩统计」上，Enter 打开统计抽屉
        onNodeWithText("游玩统计").assertIsDisplayed()
    }

    /**
     * 主菜单：**↑ 从首项绕回末项**（退出游戏），Enter 打开退出确认抽屉。
     *
     * 与难度抽屉那条互补：覆盖主菜单自己的 `ENTRY_COUNT` 循环，且验证
     * "Enter 激活当前项"走的是光标而不是焦点。
     */
    @Test
    fun mainCursorWrapsUpToExitEntry() = runComposeUiTest {
        var exited = false
        hostMenu(onExit = { exited = true })

        onNodeWithText("开始游戏").requestFocus()
        onNodeWithText("开始游戏").performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithText("开始游戏").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        // 退出是二次确认：此时只应打开抽屉，还没真的退出
        assertTrue(!exited, "打开退出确认抽屉时不应已经退出")
        onNodeWithText("退出游戏").assertIsDisplayed()
    }
}
