package org.example.sudoku.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.example.sudoku.core.Difficulty
import org.example.sudoku.core.Game
import org.example.sudoku.state.GameAction
import org.example.sudoku.state.GameState
import org.example.sudoku.state.Screen
import org.example.sudoku.state.Snapshot
import org.example.sudoku.ui.screens.BoardBackdropTag
import org.example.sudoku.ui.screens.GameScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 对局页两条"只有真机才看得出"的行为的自动验收。
 *
 * 为什么不靠实机冒烟就够：截图能看出"条在下面"，但看不出"为什么不动"——
 * 而本轮真踩到的坑恰恰是这一类（`pointerInput` 协程里读 `size.height` 会拿到 0，
 * 表现为"条永远不动"）。用真实组合 + 真实指针事件把它钉住，回归时几秒钟就能发现。
 */
@OptIn(ExperimentalTestApi::class)
class GameScreenUiTest {

    /**
     * 撤销 / 重做条跟着鼠标在棋盘上下换边，且**中间死区不动**。
     *
     * 三段连起来看：默认在下 → 指针进棋盘上部 5% → 搬到上 → 指针进正中（死区）→ 不动
     * → 指针进下部 95% → 搬回下。死区那一段验的是滞回：鼠标停在中线附近轻微晃动，
     * 条不能来回跳（一跳，指着的"撤销"就换成了"重做"）。
     */
    @Test
    fun floatingBarFollowsThePointerWithHysteresis() = runComposeUiTest {
        setContent { GameScreenHost(state) }

        val atBottom = undoTop()

        // ① 棋盘第 1 行：纵向约 5%，越过 35% 线 → 换到棋盘上方
        onNodeWithContentDescription("第 1 行第 1 列，给定 5").performMouseInput { moveTo(center) }
        waitForIdle()
        val atTop = undoTop()
        assertTrue(atTop < atBottom, "指针进棋盘上半部后条应搬到上方（$atBottom → $atTop）")

        // ② 棋盘正中：落在 35%–65% 的死区 → 保持在上方
        onNodeWithContentDescription("第 5 行第 5 列，给定 5").performMouseInput { moveTo(center) }
        waitForIdle()
        assertEquals(atTop, undoTop(), "死区内不该换边：停在中线附近轻微晃动就会让条来回跳")

        // ③ 棋盘第 9 行：纵向约 95%，越过 65% 线 → 搬回棋盘下方
        onNodeWithContentDescription("第 9 行第 9 列，填入 9").performMouseInput { moveTo(center) }
        waitForIdle()
        val backToBottom = undoTop()
        assertTrue(backToBottom > atTop, "指针进棋盘下半部后条应搬回下方（$atTop → $backToBottom）")
    }

    /**
     * "点棋盘以外的空白 → 棋盘回到干净样子"，且**点棋盘本身不会误触发**。
     *
     * 后半句才是关键：这条最容易做成"点哪儿都清"，那会把正常的选格也一起清掉。
     */
    @Test
    fun onlyClicksOutsideTheBoardClearTheSelection() = runComposeUiTest {
        val actions = mutableListOf<GameAction>()
        setContent { GameScreenHost(state) { actions += it } }

        // ① 点棋盘上的格子：只该选中，不该顺带清掉
        onNodeWithContentDescription("第 5 行第 5 列，给定 5").performMouseInput { click(center) }
        waitForIdle()
        assertTrue(actions.contains(GameAction.Select(40)), "点格子应选中它（下标 40），实际：$actions")
        assertTrue(actions.none { it == GameAction.Deselect }, "点棋盘不该清选中，实际：$actions")

        // ② 点空白层最左上角（必然在棋盘之外）：应派发 Deselect
        actions.clear()
        onNodeWithTag(BoardBackdropTag).performMouseInput { click(Offset(4f, 4f)) }
        waitForIdle()
        assertTrue(actions.contains(GameAction.Deselect), "点棋盘外的空白应清掉选中与临时高亮，实际：$actions")
        assertTrue(actions.none { it is GameAction.Select }, "点空白不该顺手选中某一格，实际：$actions")
    }

    /**
     * 悬停图标按钮 → 浮出功能名；移开 → 收起来。
     *
     * 顺带钉住一条设计约定：**提示文案可以比读屏描述短**。读屏要念完整意图
     * （"切换到夜墨模式"），而浮出来的标签必须一眼看完（"夜墨模式"）。
     * 两者混用会让按钮"读一个词、写另一个词"。
     */
    @Test
    fun hoveringAnIconTurnsItsNameUp() = runComposeUiTest {
        setContent { GameScreenHost(state) }

        // 没悬停时不该有浮层：它一出现就要占地方的
        onAllNodesWithText("夜墨模式").assertCountEquals(0)

        onNodeWithContentDescription("切换到夜墨模式").performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithText("夜墨模式").assertIsDisplayed()
    }
}

/** 撤销按钮的顶边：条在棋盘上方时它明显更小，拿它当"条此刻在哪一侧"的判据。 */
private fun SemanticsNodeInteractionsProvider.undoTop(): Float =
    onNodeWithContentDescription("撤销").fetchSemanticsNode().boundsInRoot.top

/** 给足尺寸：棋盘要能拿到"宽度"而不是被压到最小号，上下两条浮动条槽才都存在。 */
@Composable
private fun GameScreenHost(state: GameState, onAction: (GameAction) -> Unit = {}) {
    Box(Modifier.size(1000.dp, 1000.dp)) {
        GameScreen(
            state = state,
            conflicts = BooleanArray(81),
            progress = 79 to 81,
            onAction = onAction,
        )
    }
}

private const val SOLUTION =
    "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

private val Puzzle = SOLUTION.toBoard().also { it[80] = 0 }
private val Current = SOLUTION.toBoard()

private val game = Game(
    puzzle = Puzzle,
    current = Current,
    solution = SOLUTION.toBoard(),
    difficulty = Difficulty.Easy,
)

private val state = GameState(
    screen = Screen.Game,
    game = game,
    // 栈非空：撤销按钮处于可用态，取到的就是正常按钮的位置
    undoStack = listOf(
        Snapshot(
            current = Current.copyOf(),
            notes = IntArray(81),
            hintUsed = false,
            hintsCount = 0,
            revealed = false,
        ),
    ),
)

private fun String.toBoard(): IntArray = IntArray(81) { this[it].digitToInt() }
