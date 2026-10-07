package org.example.sudoku.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 浮动操作条位置策略：跟鼠标在棋盘上下之间换边，且必须**滞回**。
 *
 * 这里的每一条都对应一个真实的误触路径——尤其"死区"那两条：
 * 没有滞回时，鼠标在中线附近晃动会让"撤销"和"重做"来回错位。
 */
class FloatingBarPolicyTest {

    private val height = 600f

    @Test
    fun withoutPointerItStaysPut() {
        assertEquals(BarSide.Bottom, FloatingBarPolicy.next(BarSide.Bottom, null, 0f, height))
        assertEquals(BarSide.Top, FloatingBarPolicy.next(BarSide.Top, null, 0f, height))
        assertEquals(BarSide.Bottom, FloatingBarPolicy.Default, "无指针时的默认侧应是下方")
    }

    @Test
    fun pointerInUpperAreaMovesBarToTop() {
        // 10% 处
        assertEquals(BarSide.Top, FloatingBarPolicy.next(BarSide.Bottom, 60f, 0f, height))
    }

    @Test
    fun pointerInLowerAreaMovesBarToBottom() {
        // 90% 处
        assertEquals(BarSide.Bottom, FloatingBarPolicy.next(BarSide.Top, 540f, 0f, height))
    }

    @Test
    fun deadZoneKeepsCurrentSide() {
        // 50% 在中线附近：两侧都不该被"吸"过去
        assertEquals(BarSide.Top, FloatingBarPolicy.next(BarSide.Top, 300f, 0f, height))
        assertEquals(BarSide.Bottom, FloatingBarPolicy.next(BarSide.Bottom, 300f, 0f, height))
    }

    @Test
    fun boardTopIsSubtractedBeforeJudging() {
        // 容器顶边在 100px：120px 只相当于容器内的 3%，属于上方区域
        assertEquals(BarSide.Top, FloatingBarPolicy.next(BarSide.Bottom, 120f, 100f, height))
    }

    @Test
    fun unmeasurableHeightKeepsCurrentSide() {
        // 高度为 0（还没测量出来）时不能拿它做除法
        assertEquals(BarSide.Top, FloatingBarPolicy.next(BarSide.Top, 10f, 0f, 0f))
    }
}
