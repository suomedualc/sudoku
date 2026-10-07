package org.example.sudoku.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 两套主题的**可访问性底线**。
 *
 * 需求里写的是"两种模式下棋盘数字、网格线与背景的对比度符合可访问性标准"——
 * 这条**不能靠肉眼验收**：把一个色值改浅一档几乎看不出来，但低视力 / 强光下的用户会直接读不清。
 * 所以在这里用 WCAG 2.1 的相对亮度公式把底线钉住，改配色时先过这一关。
 *
 * 唯一的例外是四墨 [Ink.Faint]：它被规定为**只用于禁用态文字**
 * （WCAG 对禁用控件不要求对比度，它的"淡"正是要表达不可用），
 * 因此这里对它只要求"看得见（≥ 3:1）"且"确实够淡（< 4.5:1）"——后者保证它不会被误用成正文色。
 */
class InkThemeTest {

    @AfterTest
    fun restoreLightTheme() {
        // Ink 的主题是全局快照状态，测完必须还原，否则会污染同一 JVM 里的其它用例
        Ink.setDark(false)
    }

    @Test
    fun firstThreeInkLevelsMeetAaOnBothThemes() {
        for (dark in listOf(false, true)) {
            Ink.setDark(dark)
            val theme = if (dark) "夜墨" else "纸墨"
            val sheet = Ink.PaperSheet
            for ((name, ink) in listOf("一墨" to Ink.Black, "二墨" to Ink.Grey, "三墨" to Ink.Light)) {
                val ratio = contrastRatio(ink, sheet)
                assertTrue(
                    ratio >= AA_SMALL_TEXT,
                    "$theme 的$name 对棋盘纸只有 ${round2(ratio)}:1，低于 AA 的 $AA_SMALL_TEXT:1",
                )
            }
            // 棋盘纸本身与页面底纸也要有极淡的层次，否则"上面又铺了一张纸"的读法就没了
            assertTrue(
                contrastRatio(Ink.PaperSheet, Ink.Paper) > 1.0,
                "$theme 里棋盘纸应当比页面纸亮一档（层级靠纸的深浅表达）",
            )
        }
    }

    @Test
    fun faintStaysDisabledOnly() {
        for (dark in listOf(false, true)) {
            Ink.setDark(dark)
            val theme = if (dark) "夜墨" else "纸墨"
            val ratio = contrastRatio(Ink.Faint, Ink.PaperSheet)
            assertTrue(ratio >= 3.0, "$theme 的四墨只有 ${round2(ratio)}:1，禁用态也快看不见了")
            assertTrue(
                ratio < AA_SMALL_TEXT,
                "$theme 的四墨已达 AA（${round2(ratio)}:1）——那它就不该再叫\"最淡/禁用态专用\"了：" +
                    "要么把它升格为正文色并同步文档，要么调淡回去",
            )
        }
    }

    @Test
    fun nightModeReallyInvertsPaperAndInk() {
        Ink.setDark(false)
        val lightPaper = Ink.Paper
        val lightInk = Ink.Black

        Ink.setDark(true)
        assertFalse(Ink.Paper == lightPaper, "夜墨必须换掉纸色")
        assertFalse(Ink.Black == lightInk, "夜墨必须换掉墨色")
        assertTrue(
            luminance(Ink.Black) > luminance(Ink.Paper),
            "夜墨是「深底浅墨」：墨要比纸亮（名字说的是墨的层级，不是绝对色值）",
        )

        Ink.setDark(false)
        assertTrue(
            luminance(Ink.Black) < luminance(Ink.Paper),
            "纸墨是「浅底深墨」",
        )
    }

    @Test
    fun paperLayersKeepTheSameDirectionOnBothThemes() {
        // 三层纸由下往上**逐层变亮**：两套主题都成立，所以"上层纸"的读法不会随主题翻转
        for (dark in listOf(false, true)) {
            Ink.setDark(dark)
            val theme = if (dark) "夜墨" else "纸墨"
            assertTrue(luminance(Ink.Paper) > luminance(Ink.PaperShade), "$theme：底纸应比压印纸亮")
            assertTrue(luminance(Ink.PaperSheet) > luminance(Ink.Paper), "$theme：上层纸片应比底纸亮")
        }
    }
}

private const val AA_SMALL_TEXT = 4.5

/** WCAG 2.1 的相对亮度。 */
private fun luminance(color: Color): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** WCAG 2.1 对比度：(L1 + 0.05) / (L2 + 0.05)。 */
private fun contrastRatio(a: Color, b: Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

private fun round2(value: Double): Double = kotlin.math.round(value * 100) / 100
