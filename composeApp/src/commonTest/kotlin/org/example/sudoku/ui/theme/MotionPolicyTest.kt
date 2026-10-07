package org.example.sudoku.ui.theme

import org.example.sudoku.ui.theme.DesignTokens.Motion
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 减少动态效果的**纯策略**验证（平台判定调不到，策略必须先是可测的纯函数）。
 *
 * 核心：开启时时长必须是 **0（瞬时）**，而不是"缩短一半"——半速动画对前庭敏感的用户更难受。
 */
class MotionPolicyTest {

    @Test
    fun reducedMotionMakesEveryDurationInstant() {
        assertEquals(0, motionDurationMs(Motion.PressMs, reduceMotion = true))
        assertEquals(0, motionDurationMs(Motion.EnterMs, reduceMotion = true))
        assertEquals(0, motionDurationMs(Motion.ExitMs, reduceMotion = true))
        assertEquals(0, motionDurationMs(Motion.RevealMs, reduceMotion = true))
    }

    @Test
    fun normalMotionKeepsBaseDurations() {
        assertEquals(Motion.PressMs, motionDurationMs(Motion.PressMs, reduceMotion = false))
        assertEquals(Motion.EnterMs, motionDurationMs(Motion.EnterMs, reduceMotion = false))
        assertEquals(Motion.ExitMs, motionDurationMs(Motion.ExitMs, reduceMotion = false))
        assertEquals(Motion.RevealMs, motionDurationMs(Motion.RevealMs, reduceMotion = false))
    }

    @Test
    fun exitIsFasterThanEnter() {
        // 退场不该拖沓：这条是动效令牌的语义约束，改令牌时会被这条测试拦住
        assertEquals(true, Motion.ExitMs < Motion.EnterMs)
    }

    @Test
    fun revealStaysWithinItsSpec() {
        // 「落笔成局」的验收线（`docs/07` §4）：开局显影**必须 ≤ 200ms**；
        // 也不能短到看不见"由淡到浓"（< 120ms 就退化成闪一下）。改令牌会被这条拦住。
        assertEquals(
            true,
            Motion.RevealMs in 120..200,
            "开局显影时长应在 120–200ms，实际 ${Motion.RevealMs}ms",
        )
    }

    @Test
    fun revealIsFasterThanOverlayEnter() {
        // 开局不该让人等：显影比覆盖层进入更短
        assertEquals(true, Motion.RevealMs < Motion.EnterMs)
    }
}
