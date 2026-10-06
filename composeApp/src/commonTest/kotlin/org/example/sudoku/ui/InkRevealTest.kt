package org.example.sudoku.ui

import org.example.sudoku.ui.components.revealAlpha
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 「落笔成局」开局显影的**纯函数**验证：动画本身靠肉眼看，策略必须可测。
 */
class InkRevealTest {

    @Test
    fun finishedRevealIsFullyOpaqueEverywhere() {
        for (pos in 0..80) {
            assertEquals(1f, revealAlpha(1f, pos), "显影结束后每格都必须是全墨（pos=$pos）")
            assertEquals(1f, revealAlpha(1.4f, pos))
        }
    }

    @Test
    fun nothingIsVisibleAtTheStart() {
        assertEquals(0f, revealAlpha(0f, 0))
        assertEquals(0f, revealAlpha(0f, 80))
    }

    @Test
    fun topLeftWritesBeforeBottomRight() {
        // 同一个进度下，左上角一定比右下角更"写完"——这就是"从上往下写"的观感来源
        assertTrue(revealAlpha(0.3f, 0) > revealAlpha(0.3f, 80), "第 1 行第 1 列应先于第 9 行第 9 列")
        assertTrue(revealAlpha(0.6f, 4) > revealAlpha(0.6f, 76))
    }

    @Test
    fun alphaStaysWithinRangeAcrossProgress() {
        var p = 0f
        while (p <= 1.0001f) {
            for (pos in 0..80 step 7) {
                val a = revealAlpha(p, pos)
                assertTrue(a in 0f..1f, "alpha 必须落在 0..1（progress=$p, pos=$pos, alpha=$a）")
            }
            p += 0.05f
        }
    }

    @Test
    fun lastCellIsPerceptuallyCompleteJustBeforeTheEnd() {
        // 最后一格（第 9 行第 9 列）起笔最晚，但在动画结束前 0.1% 就已经接近全墨——
        // 不能让人看到最后一格"突然补齐"
        val almost = revealAlpha(0.999f, 80)
        assertTrue(almost > 0.99f, "末格在进度 99.9% 时应已接近全墨，实际 $almost")
        assertEquals(1f, revealAlpha(1f, 80))
    }
}
