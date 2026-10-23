package org.example.sudoku.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 触屏可达性底线：常驻可点击控件的边长必须 ≥ 48dp（Android / Material 最小触控目标标准）。
 *
 * 说明两点（不写进断言，但读代码时要心里有数）：
 * - `IconButton` 视觉是 36dp，但 `InkIconButton` 走 [androidx.compose.foundation.clickable]，
 *   命中区会被 Compose 自动扩到 48dp——视觉小、命中区不小；
 * - 棋盘格子是网格内的"单元格"，不属于按钮，边长由棋盘尺寸决定，不套用 48dp 标准。
 */
class HitAreaTest {

    @Test
    fun interactiveControlsMeetTouchTarget() {
        assertTrue(DesignTokens.Sizes.KeyHeight >= 48.dp, "数字键高度应 ≥48dp")
        assertTrue(DesignTokens.Sizes.PadKeySize >= 48.dp, "悬浮面板键应 ≥48dp")
        assertTrue(DesignTokens.Sizes.CompactItemHeight >= 48.dp, "紧凑按钮 / 开关行高度应 ≥48dp")
    }
}