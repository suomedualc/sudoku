package org.example.sudoku.state

import org.example.sudoku.core.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals

/** 统计记账单测：只存"事件累加"，派生值不落盘——改口径不用迁移旧存档。 */
class GameStatsTest {

    @Test
    fun recordWinCountsStreakAndFastest() {
        var stats = GameStats().recordWin(120, Difficulty.Easy)
        stats = stats.recordWin(90, Difficulty.Easy)

        assertEquals(2, stats.gamesWon)
        assertEquals(2, stats.currentStreak)
        assertEquals(2, stats.bestStreak)
        assertEquals(90, stats.fastestEasy, "更快才刷新")
        assertEquals(210, stats.totalPlaySeconds, "两次用时累加")
    }

    @Test
    fun fastestIsTrackedPerDifficulty() {
        val stats = GameStats().recordWin(100, Difficulty.Hard)
        assertEquals(100, stats.fastestHard)
        assertEquals(0, stats.fastestEasy, "别的难度仍是无记录")
    }

    @Test
    fun abandonResetsStreakButKeepsHistory() {
        var stats = GameStats().recordWin(60, Difficulty.Easy).recordWin(60, Difficulty.Easy)
        stats = stats.recordAbandon(30)

        assertEquals(0, stats.currentStreak, "放弃清零连胜")
        assertEquals(2, stats.bestStreak, "历史最长连胜保留")
        assertEquals(1, stats.gamesAbandoned)
        assertEquals(150, stats.totalPlaySeconds)
    }

    @Test
    fun derivedValues() {
        val stats = GameStats(gamesWon = 3, gamesAbandoned = 1, totalPlaySeconds = 200, totalMoves = 40)
        assertEquals(4, stats.gamesTotal)
        assertEquals(0.75, stats.winRate)
        assertEquals(5.0, stats.avgSecondsPerMove)
        assertEquals(0.0, GameStats().winRate, "没玩过不该除零")
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val stats = GameStats()
            .recordWin(120, Difficulty.Expert)
            .recordMove()
            .recordHint()
            .recordAbandon(30)
        assertEquals(stats, GameStats.decode(stats.encode()))
    }

    @Test
    fun malformedTextFallsBackToZeroes() {
        assertEquals(GameStats(), GameStats.decode(null))
        assertEquals(GameStats(), GameStats.decode("x,y"))
        // 段数多给 / 少给都按 0 补齐，不抛异常
        assertEquals(5, GameStats.decode("1,2,3,4,5,6,7,8,9,10,11,12").gamesWon + 4)
    }
}
