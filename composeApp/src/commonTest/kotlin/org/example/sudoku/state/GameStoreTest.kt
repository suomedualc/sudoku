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
 * 存档相关单测：编解码（含 v1 兼容）的健壮性 + 状态容器与存档的协作
 * （落盘 / 恢复 / 设置跨对局保留 / 结算后只留设置）。
 * 用内存实现替代文件，避免测试触碰磁盘。
 */
class GameStoreTest {

    private fun sampleGame(difficulty: Difficulty = Difficulty.Normal): Game =
        Sudoku.generate(difficulty, Random(7))

    @Test
    fun codecRoundTripsGameAndSettings() {
        val game = sampleGame()
        val notes = IntArray(81) { if (it % 7 == 0) 0b101010 else 0 }
        val settings = GameSettings(
            strictMode = true,
            showNotes = false,
            noteMode = true,
            hintCandidates = true,
            darkMode = true,
        )

        val decoded = SaveCodec.decode(SaveCodec.encode(SaveFile(settings, SavedGame(game, notes, 123))))

        assertNotNull(decoded)
        assertEquals(settings, decoded.settings, "五项设置应完整还原")
        val restored = decoded.game
        assertNotNull(restored)
        assertTrue(restored.game.puzzle.contentEquals(game.puzzle), "题面应完整还原")
        assertTrue(restored.game.current.contentEquals(game.current), "当前盘应完整还原")
        assertTrue(restored.game.solution.contentEquals(game.solution), "答案应完整还原")
        assertEquals(game.difficulty, restored.game.difficulty)
        assertTrue(restored.notes.contentEquals(notes), "笔记应完整还原")
        assertEquals(123, restored.elapsed)
    }

    @Test
    fun codecKeepsSettingsWhenThereIsNoGame() {
        val text = SaveCodec.encode(
            SaveFile(GameSettings(strictMode = true, showNotes = false, hintCandidates = true)),
        )

        val decoded = SaveCodec.decode(text)

        assertNotNull(decoded, "只有设置的存档也应可读")
        assertTrue(decoded.settings.strictMode)
        assertFalse(decoded.settings.showNotes)
        assertTrue(decoded.settings.hintCandidates)
        assertNull(decoded.game, "没有 game 块时应判为无对局")
        assertFalse(text.contains("game=1"), "无对局时不应写 game 块")
    }

    /** 更早的 v2 文件没有 `hintCandidates` 行：应取默认值（关闭），不能因此判为坏档。 */
    @Test
    fun codecDefaultsHintCandidatesWhenLineIsMissing() {
        val old = SaveCodec.encode(SaveFile(game = SavedGame(sampleGame(), IntArray(81), 0)))
            .lineSequence()
            .filterNot { it.startsWith("hintCandidates=") }
            .joinToString("\n")

        val decoded = SaveCodec.decode(old)

        assertNotNull(decoded, "缺少候选提示设置的旧文件仍应可读")
        assertFalse(decoded.settings.hintCandidates, "缺行取默认：关闭")
        assertNotNull(decoded.game)
    }

    /** 同理：本轮之前写下的存档没有 `darkMode` 行，应读成**浅色纸面**（升级前的观感）。 */
    @Test
    fun codecDefaultsDarkModeToOffWhenLineIsMissing() {
        val old = SaveCodec.encode(
            SaveFile(
                GameSettings(darkMode = true),
                SavedGame(sampleGame(), IntArray(81), 0),
            ),
        )
            .lineSequence()
            .filterNot { it.startsWith("darkMode=") }
            .joinToString("\n")

        val decoded = SaveCodec.decode(old)

        assertNotNull(decoded, "缺少夜墨设置的旧文件仍应可读")
        assertFalse(decoded.settings.darkMode, "缺行取默认：浅色纸面")
        assertNotNull(decoded.game, "设置缺行不该连累对局")
    }

    /** v1 存档没有设置行，应能读出来并取默认设置（老用户的存档不能因为升级而作废）。 */
    @Test
    fun codecReadsLegacyV1File() {
        val game = sampleGame(Difficulty.Easy)
        val legacy = buildString {
            appendLine("v=1")
            appendLine("difficulty=${game.difficulty.name}")
            appendLine("puzzle=${game.puzzle.joinToString("")}")
            appendLine("current=${game.current.joinToString("")}")
            appendLine("solution=${game.solution.joinToString("")}")
            appendLine("notes=${IntArray(81).joinToString(",")}")
            appendLine("elapsed=42")
        }

        val decoded = SaveCodec.decode(legacy)

        assertNotNull(decoded, "v1 存档必须仍可读")
        assertEquals(GameSettings(), decoded.settings, "v1 无设置行 → 取默认设置")
        val restored = decoded.game
        assertNotNull(restored)
        assertEquals(42, restored.elapsed)
        assertTrue(restored.game.puzzle.contentEquals(game.puzzle), "v1 的盘面也应完整还原")
    }

    @Test
    fun codecRejectsMalformedInput() {
        assertNull(SaveCodec.decode(""), "空内容应判为无存档")
        assertNull(SaveCodec.decode("v=3\ndifficulty=Easy"), "未知版本应判为无存档")
        assertNull(SaveCodec.decode("v=0\nstrict=1"), "版本 0 应判为无存档")
        assertNull(SaveCodec.decode("v=1\ndifficulty=不存在"), "非法难度应判为无存档")
        assertNull(SaveCodec.decode("v=2\ngame=1\ndifficulty=Easy"), "声明有对局但字段缺失应判为无存档")

        val good = SaveCodec.encode(SaveFile(game = SavedGame(sampleGame(), IntArray(81), 0)))
        val shortBoard = good.replace("puzzle=", "puzzle=12")
        assertNull(SaveCodec.decode(shortBoard), "盘面长度不足 81 应判为无存档")

        val shortNotes = good.replace(Regex("notes=[^\\n]*"), "notes=1,2,3")
        assertNull(SaveCodec.decode(shortNotes), "笔记数量不符应判为无存档")

        val negative = good.replace(Regex("elapsed=\\d+"), "elapsed=-5")
        assertNull(SaveCodec.decode(negative), "负计时应判为无存档")
    }

    /**
     * 难度只是个**标签**：它坏了不该让整份存档作废——存档是外部输入，
     * 与"宽松取默认"（缺行 / 非法值回默认）是同一条策略。
     */
    @Test
    fun codecFallsBackToNormalWhenDifficultyIsUnknown() {
        val text = SaveCodec.encode(SaveFile(game = SavedGame(sampleGame(), IntArray(81), 7)))
            .replace("difficulty=Normal", "difficulty=不存在")

        val decoded = SaveCodec.decode(text)

        assertNotNull(decoded, "难度坏掉不该让整份存档作废")
        val restored = decoded!!.game
        assertNotNull(restored, "盘面完好时应保住对局")
        assertEquals(Difficulty.Normal, restored.game.difficulty, "未知难度退回 Normal")
        assertEquals(7, restored.elapsed, "其余字段应照常还原")
    }

    /**
     * 落盘只发生在"改动了可持久数据"的动作上：[GameAction.Select] / [GameAction.Move]
     * 只改当前选中格，而存档里根本没有这一项——否则每移动一次光标都要重写整个文件。
     */
    @Test
    fun movingCursorDoesNotRewriteSaveFile() {
        val store = CountingGameStore()
        val viewModel = GameViewModel(seed = 7, store = store)
        viewModel.dispatch(GameAction.NewGame(Difficulty.Easy))
        val afterNewGame = store.saveCount

        repeat(10) { viewModel.dispatch(GameAction.Select(it)) }

        assertEquals(afterNewGame, store.saveCount, "Select 不改动可持久数据，不应触发落盘")

        viewModel.dispatch(GameAction.Select(40))
        viewModel.dispatch(GameAction.Digit(1))
        assertTrue(store.saveCount > afterNewGame, "落子改动了盘面，应落盘")
    }

    @Test
    fun settingsSurviveRestartAndNewGame() {
        val store = InMemoryGameStore()
        val first = GameViewModel(seed = 9, store = store)
        assertFalse(first.state.strictMode, "默认严格模式关闭")
        first.dispatch(GameAction.ToggleStrict(true))
        first.dispatch(GameAction.ToggleShowNotes)
        first.dispatch(GameAction.ToggleHintCandidates)
        first.dispatch(GameAction.ToggleDarkMode)
        first.dispatch(GameAction.BindKey(KeyAction.Up, "W"))
        first.dispatch(GameAction.BindKey(KeyAction.Erase, "X"))

        // 模拟重启
        val second = GameViewModel(seed = 9, store = store)
        assertTrue(second.state.strictMode, "严格模式应随存档保留")
        assertFalse(second.state.showNotes, "显示笔记应随存档保留")
        assertTrue(second.state.hintCandidates, "候选提示应随存档保留")
        assertTrue(second.state.darkMode, "夜墨模式应随存档保留")
        assertEquals("W", second.state.keyMap.up, "键位应随存档保留")
        assertEquals("X", second.state.keyMap.erase, "擦除键位应随存档保留")

        // 新开一局不应把偏好重置回默认值
        second.dispatch(GameAction.NewGame(Difficulty.Easy))
        assertTrue(second.state.strictMode, "新开一局应继承偏好")
        assertFalse(second.state.showNotes, "新开一局应继承偏好")
        assertTrue(second.state.hintCandidates, "新开一局应继承偏好")
        assertTrue(second.state.darkMode, "新开一局应继承偏好")
        assertEquals("W", second.state.keyMap.up, "新开一局应继承键位")
        assertEquals("X", second.state.keyMap.erase, "新开一局应继承擦除键位")
        assertNotNull(store.load()?.settings, "偏好应继续落盘")
    }

    @Test
    fun viewModelPersistsAndRestoresGame() {
        val store = InMemoryGameStore()
        var now = 0L
        val first = GameViewModel(seed = 11, store = store, clock = { now })
        first.dispatch(GameAction.NewGame(Difficulty.Easy))

        val pos = (0..80).first { first.state.game!!.current[it] == 0 }
        first.dispatch(GameAction.Select(pos))
        first.dispatch(GameAction.Digit(4))
        // 真实时间走 10 秒：syncClock 写入 elapsed，并在整十秒处落盘
        now += 10_000
        first.syncClock()
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
        assertNotNull(store.load()?.game, "回到首页不应丢掉对局")
    }

    @Test
    fun winningDropsTheGameButKeepsSettings() {
        val store = InMemoryGameStore()
        val viewModel = GameViewModel(seed = 11, store = store)
        viewModel.dispatch(GameAction.ToggleStrict(true))
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
        val saved = store.load()
        assertNotNull(saved, "通关后仍应有存档文件——但里面只剩设置")
        assertNull(saved.game, "通关后不应再留下可继续的对局")
        assertTrue(saved.settings.strictMode, "通关不应清掉偏好")
    }

    @Test
    fun resetKeepsTheGameResumable() {
        val store = InMemoryGameStore()
        val viewModel = GameViewModel(seed = 5, store = store)
        viewModel.dispatch(GameAction.NewGame(Difficulty.Hard))
        viewModel.dispatch(GameAction.Select(0))
        viewModel.dispatch(GameAction.Reset)

        assertTrue(viewModel.canResume, "重置后对局仍可继续")
        val saved = store.load()?.game
        assertNotNull(saved)
        assertTrue(saved.game.current.contentEquals(saved.game.puzzle), "存档里应是重置后的盘面")
    }
}

/** 测试用内存存档。 */
private class InMemoryGameStore : GameStore {
    private var saved: SaveFile? = null

    override fun load(): SaveFile? = saved

    override fun save(file: SaveFile) {
        this.saved = file
    }
}

/** 只数落盘次数的存档：用来断言"什么动作该写盘"。 */
private class CountingGameStore : GameStore {
    var saveCount: Int = 0
        private set

    override fun load(): SaveFile? = null

    override fun save(file: SaveFile) {
        saveCount++
    }
}
