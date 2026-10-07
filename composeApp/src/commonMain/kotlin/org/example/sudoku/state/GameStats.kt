package org.example.sudoku.state

import org.example.sudoku.core.Difficulty

/**
 * 终身游玩统计（**随存档落盘**，跨对局累计）。
 *
 * 字段设计（只存"事件累加出来的数"，其余全部**派生**）：
 *
 * | 字段 | 记什么 | 为什么这样记 |
 * |---|---|---|
 * | `gamesWon` | 完成的对局数 | 胜场的分母 / 分子都从这里来 |
 * | `gamesAbandoned` | 放弃的对局数（未完成就开新局、或看答案认输） | 胜率需要"输"这一侧；**继续未完成的对局不算放弃**（它是可续的） |
 * | `totalPlaySeconds` | 累计游玩时长 | 在**结算点**（通关 / 放弃）把当局用时累进来，而不是每秒写盘 |
 * | `totalMoves` | 累计填数步数（成功的填入） | "平均每步耗时 = 总时长 / 总步数"由它派生 |
 * | `totalHints` | 累计提示次数 | |
 * | `currentStreak` / `bestStreak` | 当前 / 最长连胜 | 连胜只在通关时 +1；放弃与看答案清零 |
 * | `fastest*` | 各难度最快通关（秒，0 = 无记录） | 一难度一个数，避免存一张表 |
 *
 * 派生值：`gamesTotal`、`winRate`、`avgSecondsPerMove`——**不落盘**，改口径不用迁移旧存档。
 */
data class GameStats(
    val gamesWon: Int = 0,
    val gamesAbandoned: Int = 0,
    val totalPlaySeconds: Int = 0,
    val totalMoves: Int = 0,
    val totalHints: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val fastestEasy: Int = 0,
    val fastestNormal: Int = 0,
    val fastestHard: Int = 0,
    val fastestExpert: Int = 0,
) {
    val gamesTotal: Int get() = gamesWon + gamesAbandoned

    /** 胜率 0.0–1.0；没玩过返回 0。 */
    val winRate: Double
        get() = if (gamesTotal == 0) 0.0 else gamesWon.toDouble() / gamesTotal

    /** 平均每步耗时（秒）；没走过子返回 0。 */
    val avgSecondsPerMove: Double
        get() = if (totalMoves == 0) 0.0 else totalPlaySeconds.toDouble() / totalMoves

    /** 某难度的最快通关（秒，0 = 无记录）。 */
    fun fastest(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.Easy -> fastestEasy
        Difficulty.Normal -> fastestNormal
        Difficulty.Hard -> fastestHard
        Difficulty.Expert -> fastestExpert
    }

    /** 通关记账：连胜 +1、总时长累加、最快纪录刷新（[elapsed] 秒）。 */
    fun recordWin(elapsed: Int, difficulty: Difficulty): GameStats {
        val oldBest = fastest(difficulty)
        val newBest = when {
            oldBest == 0 -> elapsed.coerceAtLeast(1) // 该难度首胜：无论多少秒都算纪录
            elapsed in 1 until oldBest -> elapsed // 更快（且是合法的正数）才刷新
            else -> oldBest
        }
        val streak = currentStreak + 1
        return copy(
            gamesWon = gamesWon + 1,
            totalPlaySeconds = totalPlaySeconds + elapsed,
            currentStreak = streak,
            bestStreak = maxOf(bestStreak, streak),
            fastestEasy = if (difficulty == Difficulty.Easy) newBest else fastestEasy,
            fastestNormal = if (difficulty == Difficulty.Normal) newBest else fastestNormal,
            fastestHard = if (difficulty == Difficulty.Hard) newBest else fastestHard,
            fastestExpert = if (difficulty == Difficulty.Expert) newBest else fastestExpert,
        )
    }

    /** 放弃记账：连胜清零、总时长累加（[elapsed] 秒）。 */
    fun recordAbandon(elapsed: Int): GameStats = copy(
        gamesAbandoned = gamesAbandoned + 1,
        totalPlaySeconds = totalPlaySeconds + elapsed,
        currentStreak = 0,
    )

    /** 一步成功填入。 */
    fun recordMove(): GameStats = copy(totalMoves = totalMoves + 1)

    /** 用了一次提示。 */
    fun recordHint(): GameStats = copy(totalHints = totalHints + 1)

    fun encode(): String = listOf(
        gamesWon, gamesAbandoned, totalPlaySeconds, totalMoves, totalHints,
        currentStreak, bestStreak, fastestEasy, fastestNormal, fastestHard, fastestExpert,
    ).joinToString(",")

    companion object {
        /** 存档是外部输入：段数不够或数字非法的段按 0 计（统计错了不能让它废掉整个存档）。 */
        fun decode(text: String?): GameStats {
            if (text.isNullOrBlank()) return GameStats()
            val n = text.split(',').map { it.trim().toIntOrNull() ?: 0 }
            fun at(i: Int) = n.getOrElse(i) { 0 }.coerceAtLeast(0)
            return GameStats(
                gamesWon = at(0), gamesAbandoned = at(1), totalPlaySeconds = at(2),
                totalMoves = at(3), totalHints = at(4), currentStreak = at(5), bestStreak = at(6),
                fastestEasy = at(7), fastestNormal = at(8), fastestHard = at(9), fastestExpert = at(10),
            )
        }
    }
}
