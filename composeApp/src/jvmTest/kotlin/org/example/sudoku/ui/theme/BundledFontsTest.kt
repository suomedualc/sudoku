package org.example.sudoku.ui.theme

import java.awt.Font
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 打包字体资产的**真实性**验证。
 *
 * 这类问题只有真正读二进制才验得出：文件在不在 classpath、是不是合法 TTF、
 * 中文/数字各归哪一份——文件名对了不代表字体对了（可能下载成 HTML 或放错权重）。
 */
class BundledFontsTest {

    @Test
    fun wenkaiIsBundledAndDisplaysCjk() {
        val font = load("/fonts/LXGWWenKai-Regular.ttf")
        assertNotNull(font, "霞鹜文楷字体资源缺失（resources/fonts）")

        val family = font.family
        val lower = family.lowercase()
        assertTrue(
            lower.contains("wenkai") || lower.contains("lxgw") ||
                family.contains("文楷") || family.contains("霞鹜"),
            "应识别为霞鹜文楷，实际 family=$family（可能下载成了错误文件）",
        )
        assertTrue(font.canDisplay('数'), "霞鹜文楷应能显示中文")
    }

    @Test
    fun nunitoIsBundledAndDisplaysDigitsOnly() {
        val font = load("/fonts/Nunito.ttf")
        assertNotNull(font, "Nunito 字体资源缺失（resources/fonts）")

        assertTrue(font.canDisplay('5'), "Nunito 应能显示数字")
        assertTrue(!font.canDisplay('数'), "Nunito 是西文/数字字体，不应包含中文（否则数字字体会被误当正文用）")
    }

    @Test
    fun themeFamiliesResolveFromBundledFonts() {
        assertNotNull(textFontFamily(), "正文 FontFamily 应解析成功（打包霞鹜文楷）")
        assertNotNull(digitFontFamily(), "数字 FontFamily 应解析成功（打包 Nunito）")
    }

    @Test
    fun digitFamilyIsNunitoWithCjkFallback() {
        val family = digitFontFamily()
        assertNotNull(family, "数字 FontFamily 应解析成功")
        // 数字族必须是回退链：Nunito 优先 + 霞鹜文楷兜底。
        // 少了兜底，一旦数字样式的位置上出现中文（如暂停时计时位显示「已暂停」）就会渲染成豆腐块。
        assertEquals(2, digitFontChain().size, "数字族应是「Nunito + 霞鹜文楷」两条回退链")
    }

    private fun load(resource: String): Font? = try {
        val stream = javaClass.getResourceAsStream(resource) ?: return null
        Font.createFonts(stream).firstOrNull()
    } catch (t: Throwable) {
        null
    }
}
