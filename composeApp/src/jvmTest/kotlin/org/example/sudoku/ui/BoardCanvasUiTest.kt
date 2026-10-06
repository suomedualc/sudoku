package org.example.sudoku.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.example.sudoku.core.Difficulty
import org.example.sudoku.core.Game
import org.example.sudoku.ui.components.BoardCanvas
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 棋盘**格子级语义**的端到端验证（真实组合 + 真实语义树）。
 *
 * 单测只能验文案（见 `BoardSemanticsTest`）；这里验的是"装配"：棋盘是否真的暴露了
 * 81 个语义节点——此前整盘只有一个节点，读屏用户根本无法逐格浏览。
 */
@OptIn(ExperimentalTestApi::class)
class BoardCanvasUiTest {

    @Test
    fun everyCellExposesItsOwnSemanticsNode() = runComposeUiTest {
        setContent { Board() }

        // 每个格子的文案都含"列"（位置描述），用子串即可选中全部格子节点
        onAllNodesWithContentDescription("列", substring = true)
            .assertCountEquals(81) // 此前整盘只有 1 个节点
    }

    @Test
    fun cellLabelsCarryPositionAndContent() = runComposeUiTest {
        setContent { Board() }

        onNodeWithContentDescription("第 1 行第 1 列，给定 5").assertIsDisplayed()
        onNodeWithContentDescription("第 1 行第 3 列，空，笔记 1、3").assertIsDisplayed()
        onNodeWithContentDescription("第 9 行第 9 列，填入 9").assertIsDisplayed()
    }

    @Test
    fun selectedCellIsAnnounced() = runComposeUiTest {
        setContent {
            Box(Modifier.size(BOARD_DP.dp)) {
                BoardCanvas(
                    game = game,
                    selected = 80,
                    notes = notes,
                    conflicts = BooleanArray(81),
                    noteMode = false,
                    onCellClick = { _, _ -> },
                )
            }
        }

        onNodeWithContentDescription("第 9 行第 9 列，已选中，填入 9").assertIsDisplayed()
        // 其余格子不受影响
        onNodeWithContentDescription("第 1 行第 1 列，给定 5").assertIsDisplayed()
    }

    @Test
    fun boardStillReportsClicks() = runComposeUiTest {
        var clicked: Int? = null
        setContent {
            Box(Modifier.size(BOARD_DP.dp)) {
                BoardCanvas(
                    game = game,
                    selected = null,
                    notes = notes,
                    conflicts = BooleanArray(81),
                    noteMode = false,
                    onCellClick = { cell, _ -> clicked = cell },
                )
            }
        }

        // 语义层盖在画布之上，但不得吞掉点击——点第 2 行第 2 列（下标 10）
        onNodeWithContentDescription("第 2 行第 2 列，给定 7")
            .performTouchInput { click(center) }
        assertEquals(10, clicked, "语义层不能吃掉点击：点第 2 行第 2 列应回传下标 10")
    }

    @Composable
    private fun Board() {
        Box(Modifier.size(BOARD_DP.dp)) {
            BoardCanvas(
                game = game,
                selected = null,
                notes = notes,
                conflicts = BooleanArray(81),
                noteMode = false,
                onCellClick = { _, _ -> },
            )
        }
    }
}

private const val BOARD_DP = 360

private const val SOLUTION =
    "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

private val Puzzle = SOLUTION.toBoard().also {
    it[2] = 0
    it[4] = 0
    it[80] = 0
}

private val Current = Puzzle.copyOf().also { it[80] = 9 }

private val notes = IntArray(81).also { it[2] = (1 shl 0) or (1 shl 2) }

private val game = Game(
    puzzle = Puzzle,
    current = Current,
    solution = SOLUTION.toBoard(),
    difficulty = Difficulty.Easy,
)

private fun String.toBoard(): IntArray = IntArray(81) { this[it].digitToInt() }
