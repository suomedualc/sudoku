package org.example.sudoku.ui.screens

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 布局形态判据（纯函数）的单测：不依赖真机旋转，宽高比较即可断言。
 *
 * 判据 = **宽 > 高 且 高 < `DesignTokens.Sizes.LandscapeMaxHeight`（480dp）**。
 * 三者必须一致：实现（[gameLayout]）、令牌（480dp）、`docs/10` 迭代 B2 的断点定义。
 */
class GameLayoutTest {

    @Test
    fun detectsLandscapeForShortWideScreens() {
        // 手机横屏的真实尺寸档：Pixel 类 891×411dp、常见 800×400dp
        assertEquals(GameLayout.Landscape, gameLayout(800.dp, 400.dp), "手机横屏（高 < 480dp）应走双栏")
        assertEquals(GameLayout.Landscape, gameLayout(891.dp, 411.dp), "Pixel 横屏应走双栏")
        assertEquals(GameLayout.Landscape, gameLayout(480.dp, 479.dp), "刚低于高度上限且宽 > 高，应走双栏")
    }

    @Test
    fun detectsPortraitWhenTallerOrSquare() {
        assertEquals(GameLayout.Portrait, gameLayout(400.dp, 800.dp), "高明显大于宽应判竖屏")
        assertEquals(GameLayout.Portrait, gameLayout(400.dp, 400.dp), "等宽高按竖屏（纵列）处理")
    }

    /**
     * 高度充裕的宽窗口**不是**横屏：这是本条断点的核心收益。
     *
     * 桌面默认窗口 1180×900 若被判为横屏，棋盘会从 ~500dp 压到 ~400dp、1–9 横排被挤成九宫格，
     * 而 `docs/02` §7 明确"对局页只有一条纵列"。加高度上限后它们保持纵列。
     */
    @Test
    fun wideButTallWindowsStayPortrait() {
        assertEquals(GameLayout.Portrait, gameLayout(1180.dp, 900.dp), "桌面默认窗口高度充裕，应保持纵列")
        assertEquals(GameLayout.Portrait, gameLayout(1024.dp, 768.dp), "平板横屏高度充裕，应保持纵列")
        assertEquals(GameLayout.Portrait, gameLayout(1200.dp, 480.dp), "恰好等于上限不算横屏（严格小于）")
    }
}
