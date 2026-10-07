package org.example.sudoku.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.example.sudoku.ui.components.InkSnackbarHostState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 自绘墨条状态容器的行为契约（替代原先第三方组件库 `SnackbarHostState` 的两条语义）。
 *
 * 放在 jvmTest 而不是 commonTest：这里要用 `runBlocking`，而 coroutines 依赖由
 * `compose.desktop.currentOs` 带进来（jvmTest 显式声明了它）。
 */
class InkSnackbarHostStateTest {

    @Test
    fun identicalMessagesEachGetANewId() = runBlocking {
        val state = InkSnackbarHostState()

        val first = launch { state.showSnackbar("同一句", durationMs = 10_000) }
        delay(50)
        val id1 = state.current?.id
        first.cancel()

        val second = launch { state.showSnackbar("同一句", durationMs = 10_000) }
        delay(50)
        val id2 = state.current?.id
        second.cancel()

        assertNotNull(id1, "第一条应当已经显示")
        assertNotNull(id2, "第二条应当已经显示")
        // 相同文案必须是"新的一条"，否则 UI 不会重新播入场动画——玩家会以为按键没反应
        assertTrue(id2 > id1, "相同文案也必须拿到新的 id（实际 $id1 -> $id2）")
    }

    @Test
    fun expiredMessageDoesNotClearTheNewerOne() = runBlocking {
        val state = InkSnackbarHostState()

        val first = launch { state.showSnackbar("先来的", durationMs = 60) }
        val second = launch { state.showSnackbar("后来的", durationMs = 5_000) }
        first.join() // 等到第一条自然过期

        assertEquals("后来的", state.current?.text, "过期的旧提示不能清掉后来顶上的那条")
        second.cancel()
    }

    @Test
    fun currentIsNullAfterTheMessageExpires() = runBlocking {
        val state = InkSnackbarHostState()
        launch { state.showSnackbar("一闪而过", durationMs = 50) }.join()
        assertEquals(null, state.current, "到期后应当清空，墨条才会退场")
    }
}
