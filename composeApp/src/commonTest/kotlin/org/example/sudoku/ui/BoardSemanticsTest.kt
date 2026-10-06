package org.example.sudoku.ui

import org.example.sudoku.core.Difficulty
import org.example.sudoku.core.Game
import org.example.sudoku.ui.components.cellA11yLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 格子读屏文案的**纯函数**验证（UI 只负责"有没有 81 个节点"，文案在这里验）。
 *
 * 文案结构：`第 R 行第 C 列` + 选中状态 + 内容（给定 / 填入 / 空 + 笔记）+ 冲突。
 */
class BoardSemanticsTest {

    @Test
    fun givenCellIsAnnouncedWithItsValue() {
        assertEquals("第 1 行第 1 列，给定 5", label(pos = 0))
        assertEquals("第 1 行第 2 列，给定 3", label(pos = 1))
    }

    @Test
    fun selectedCellIsMarkedAsSelected() {
        assertEquals("第 1 行第 1 列，已选中，给定 5", label(pos = 0, selected = 0))
    }

    @Test
    fun emptyCellWithoutNotesIsAnnouncedAsEmpty() {
        // puzzle 与 current 在第 5 格（第 1 行第 5 列）都是空，且没有笔记
        assertEquals("第 1 行第 5 列，空", label(pos = 4))
    }

    @Test
    fun emptyCellWithNotesListsThePlayerNotes() {
        // 第 1 行第 3 列：空，玩家记了 1、3
        assertEquals("第 1 行第 3 列，空，笔记 1、3", label(pos = 2))
    }

    @Test
    fun playerFilledCellIsAnnouncedAsFilled() {
        // 第 9 行第 9 列：玩家填的 9（不是题目给定）
        assertEquals("第 9 行第 9 列，填入 9", label(pos = 80))
    }

    @Test
    fun conflictingCellIsAnnounced() {
        assertEquals("第 9 行第 9 列，填入 9，与同行列宫重复", label(pos = 80, conflicts = BooleanArray(81).also { it[80] = true }))
    }

    @Test
    fun labelsAreUniquePerPosition() {
        val labels = (0..80).map { label(it) }.toSet()
        assertEquals(81, labels.size, "81 个格子的文案必须互不相同，否则读屏无法定位")
    }
}

// ---- 夹具：一盘"两个空格 + 其中一格玩家已填"的定局 ----

private const val SOLUTION =
    "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

/** 题目：末格（下标 80）与下标 2、4 是空格，其余是给定。 */
private val Puzzle = SOLUTION.toBoard().also {
    it[2] = 0
    it[4] = 0
    it[80] = 0
}

/** 当前：玩家把末格填了 9（与 solution 一致，但这里要验的是"填入"而非"给定"）。 */
private val Current = Puzzle.copyOf().also { it[80] = 9 }

private val Notes = IntArray(81).also { it[2] = (1 shl 0) or (1 shl 2) }   // 1、3

private val Game0 = Game(
    puzzle = Puzzle,
    current = Current,
    solution = SOLUTION.toBoard(),
    difficulty = Difficulty.Easy,
)

private fun String.toBoard(): IntArray = IntArray(81) { this[it].digitToInt() }

private fun label(
    pos: Int,
    selected: Int? = null,
    conflicts: BooleanArray = BooleanArray(81),
): String = cellA11yLabel(game = Game0, pos = pos, notes = Notes, conflicts = conflicts, selected = selected)
