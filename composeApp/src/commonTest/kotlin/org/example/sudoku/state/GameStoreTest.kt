package org.example.sudoku.state

import org.example.sudoku.core.Difficulty
import org.example.sudoku.core.Game
import org.example.sudoku.core.Sudoku
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 存档相关单测：编解码的健壮性 + 状态容器与存档的协作（落盘 / 恢复 / 结算清档）。
 * 用内存实现替代文件，避免测试触碰磁盘。
 */
class GameStoreTest {

    private fun sampleGame(difficulty: Difficulty = Difficulty.Normal): Game =
        Sudoku.generate(difficulty, Random(7))

    @Test
    fun codecRoundTripsAllFields() {
        val game = sampleGame()
        val notes = IntArray(81) { if (it % 7 == 0) 0b101010 else 0 }
        val saved = SavedGame(game, notes, 123)

        val decoded = SaveCodec.decode(SaveCodec.encode(saved))

        assertNotNull(decoded)
        assertTrue(decoded.game.puzzle.contentEquals(game.puzzle), "题面应完整还原")
        assertTrue(decoded.game.current.contentEquals(game.current), "当前盘应完整还原")
        assertTrue(decoded.game.solution.contentEquals(game.solution), "答案应完整还原")
        assertEquals(game.difficulty, decoded.game.difficulty)
        assertTrue(decoded.notes.contentEquals(notes), "笔记应完整还原")
        assertEquals(123, decoded.elapsed)
    }

    @Test
    fun codecRejectsMalformedInput() {
        assertNull(SaveCodec.decode(""), "空内容应判为无存档")
        assertNull(SaveCodec.decode("v=2\ndifficulty=Easy"), "版本不匹配应判为无存档")
        assertNull(SaveCodec.decode("v=1\ndifficulty=不存在"), "非法难度应判为无存档")

        val good = SaveCodec.encode(SavedGame(sampleGame(), IntArray(81), 0))
        val shortBoard = good.replace("puzzle=", "puzzle=12")
        assertNull(SaveCodec.decode(shortBoard), "盘面长度不足 81 应判为无存档")

        val shortNotes = good.replace(Regex("notes=[^\\n]*"), "notes=1,2,3")
        assertNull(SaveCodec.decode(shortNotes), "笔记数量不符应判为无存档")
    }

    @Test
    fun viewModelPersistsAndRestoresGame() {
        val store = InMemoryGameStore()
        val first = GameViewModel(seed = 11, store = store)
        first.dispatch(GameAction.NewGame(Difficulty.Easy))

        val pos = (0..80).first { first.state.game!!.current[it] == 0 }
        first.dispatch(GameAction.Select(pos))
        first.dispatch(GameAction.Digit(4))
        repeat(10) { first.dispatch(GameAction.Tick) }
        assertTrue(first.canResume, "有未完成对局时应可继续")

        // 模拟重启：同一份存档交给新的状态容器
        val second = GameViewModel(seed = 11, store = store)
        assertNotNull(second.state.game, "重启后应恢复到未完成对局")
        assertEquals(Screen.Menu, second.state.screen, "恢复后仍停在首页，由玩家决定是否继续")
        assertEquals(4, second.state.game!!.current[pos], "盘面应还原")
        assertEquals(first.state.elapsed, second.state.elapsed, "计时应还原")
        assertTrue(second.canResume)
    }

    @Test
    fun navigatingToMenuKeepsGameResumable() {
        val store = InMemoryGameStore()
        val viewModel = GameViewModel(seed = 3, store = store)
        viewModel.dispatch(GameAction.NewGame(Difficulty.Easy))
        viewModel.dispatch(GameAction.Navigate(Screen.Menu))

        assertEquals(Screen.Menu, viewModel.state.screen)
        assertTrue(viewModel.canResume)
        assertNotNull(store.load(), "回到首页不应丢掉对局")
    }

    @Test
    fun winningClearsTheSave() {
        val store = InMemoryGameStore()
        val viewModel = GameViewModel(seed = 11, store = store)
        viewModel.dispatch(GameAction.NewGame(Difficulty.Easy))

        val givens = viewModel.state.game!!.current.copyOf()
        val solution = viewModel.state.game!!.solution
        for (pos in 0..80) {
            if (givens[pos] != 0) continue
            viewModel.dispatch(GameAction.Select(pos))
            viewModel.dispatch(GameAction.Digit(solution[pos]))
        }

        assertTrue(viewModel.state.won, "填满正确盘面应判胜")
        assertFalse(viewModel.canResume, "通关后不应再提示继续")
        assertNull(store.load(), "通关后应清档")
    }

    @Test
    fun resetKeepsTheGameResumable() {
        val store = InMemoryGameStore()
        val viewModel = GameViewModel(seed = 5, store = store)
        viewModel.dispatch(GameAction.NewGame(Difficulty.Hard))
        viewModel.dispatch(GameAction.Select(0))
        viewModel.dispatch(GameAction.Reset)

        assertTrue(viewModel.canResume, "重置后对局仍可继续")
        val saved = store.load()
        assertNotNull(saved)
        assertTrue(saved.game.current.contentEquals(saved.game.puzzle), "存档里应是重置后的盘面")
    }
}

/** 测试用内存存档。 */
private class InMemoryGameStore : GameStore {
    private var saved: SavedGame? = null

    override fun load(): SavedGame? = saved

    override fun save(saved: SavedGame) {
        this.saved = saved
    }

    override fun clear() {
        saved = null
    }
}
