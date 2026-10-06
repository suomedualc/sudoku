package org.example.sudoku.state

import org.example.sudoku.core.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 计时与事件通道单测：注入假时钟，**不依赖真实 sleep**，因此确定且飞快。
 *
 * 覆盖的回归点：
 * - 计时按真实时间取整到秒（旧实现是"每次心跳 +1"，会漂移）；
 * - 暂停 / 离开对局界面停表，恢复后从暂停处继续；
 * - 重置重新计时；
 * - 相同文案的连续提示都会被送达（旧实现第二次会被等值比较吞掉）。
 */
class GameClockTest {

    private var now = 0L

    private fun viewModel(store: GameStore = NoopGameStore) =
        GameViewModel(seed = 11, store = store, clock = { now })

    @Test
    fun clockCountsRealTimeInsteadOfTicks() {
        val vm = viewModel()
        vm.dispatch(GameAction.NewGame(Difficulty.Easy))

        now += 4_800
        vm.syncClock()
        assertEquals(4, vm.state.elapsed, "4.8 秒应取整为 4")

        now += 5_300
        vm.syncClock()
        assertEquals(10, vm.state.elapsed, "累计 10.1 秒应取整为 10")
    }

    @Test
    fun clockStopsWhilePausedOrAwayAndResumesAfterwards() {
        val vm = viewModel()
        vm.dispatch(GameAction.NewGame(Difficulty.Easy))

        now += 3_000
        vm.syncClock()
        assertEquals(3, vm.state.elapsed)

        vm.dispatch(GameAction.Pause)
        now += 60_000
        vm.syncClock()
        assertEquals(3, vm.state.elapsed, "暂停 1 分钟不应走表")

        vm.dispatch(GameAction.Resume)
        now += 2_000
        vm.syncClock()
        assertEquals(5, vm.state.elapsed, "恢复后应从暂停处继续")

        vm.dispatch(GameAction.Navigate(Screen.Menu))
        now += 30_000
        vm.syncClock()
        assertEquals(5, vm.state.elapsed, "离开对局界面同样停表")
    }

    @Test
    fun resetRestartsClockFromZero() {
        val vm = viewModel()
        vm.dispatch(GameAction.NewGame(Difficulty.Easy))

        now += 7_000
        vm.syncClock()
        assertEquals(7, vm.state.elapsed)

        vm.dispatch(GameAction.Reset)
        assertEquals(0, vm.state.elapsed, "重置应立即清零")

        now += 2_000
        vm.syncClock()
        assertEquals(2, vm.state.elapsed, "重置后应重新计时")
    }

    @Test
    fun identicalMessagesAreAllDelivered() {
        val vm = viewModel()
        vm.dispatch(GameAction.NewGame(Difficulty.Easy))

        // 未选格就按数字：两次都要收到提示（旧实现第二次会被吞掉）
        vm.dispatch(GameAction.Digit(5))
        vm.dispatch(GameAction.Digit(5))

        val first = vm.messages.tryReceive().getOrNull()
        val second = vm.messages.tryReceive().getOrNull()
        assertEquals("请先选择一个格子", first)
        assertEquals(first, second, "相同文案应逐条送达")
    }

    @Test
    fun pauseOnMinimizeIsIdempotent() {
        val vm = viewModel()
        vm.dispatch(GameAction.NewGame(Difficulty.Easy))
        vm.dispatch(GameAction.Pause)
        vm.dispatch(GameAction.Pause)

        assertTrue(vm.state.paused)
        now += 5_000
        vm.syncClock()
        assertEquals(0, vm.state.elapsed, "暂停期间不应计时")
    }
}
