package org.example.sudoku.ui

import org.example.sudoku.ui.components.InkSnackbarHostState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 自绘墨条状态容器的行为契约。
 *
 * 显示语义是**顶替式**（不排队）：`show` 立即顶掉当前那条，固定停留时长的计时
 * 在挂载点（[org.example.sudoku.ui.components.InkSnackbarHost]）的协程里，由
 * `InkSnackbarHostUiTest` 用测试时钟钉住"与点击频率无关"。
 *
 * 放在 jvmTest：与其它 UI 测试同源集，便于一起跑（纯状态断言，不需要协程——
 * `show` 不再挂起，"排队"语义已随时长累加缺陷一起移除）。
 */
class InkSnackbarHostStateTest {

    @Test
    fun identicalMessagesEachGetANewId() {
        val state = InkSnackbarHostState()

        state.show("同一句")
        val id1 = state.current?.id
        state.show("同一句")
        val id2 = state.current?.id

        assertNotNull(id1, "第一条应当已经显示")
        assertNotNull(id2, "第二条应当立即顶掉第一条")
        assertEquals("同一句", state.current?.text)
        // 相同文案必须是"新的一条"，否则 UI 不会重新播入场动画——玩家会以为按键没反应
        assertTrue(id2 > id1, "相同文案也必须拿到新的 id（实际 $id1 -> $id2）")
    }

    @Test
    fun clearIfCurrentOnlyClearsItsOwnMessage() {
        val state = InkSnackbarHostState()

        state.show("先来的")
        val staleId = state.current!!.id
        state.show("后来的") // 顶掉"先来的"，计时随之重启

        state.clearIfCurrent(staleId)
        assertEquals(
            "后来的", state.current?.text,
            "到点的旧计时器不能清掉顶替它的新提示",
        )

        state.clearIfCurrent(state.current!!.id)
        assertEquals(null, state.current, "自己的计时器到点后应当清空，墨条才会退场")
    }
}
