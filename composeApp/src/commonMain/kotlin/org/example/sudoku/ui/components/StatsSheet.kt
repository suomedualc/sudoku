package org.example.sudoku.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.sudoku.core.Difficulty
import org.example.sudoku.core.Sudoku
import org.example.sudoku.state.GameStats
import org.example.sudoku.ui.i18n.Strings
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink
import org.example.sudoku.ui.theme.inkRoundRect
import kotlin.math.roundToInt

/**
 * 游玩统计抽屉的内容：**只读**——数字是"履历"，不提供清零入口
 * （清零 = 把玩家的记录抹掉，这种操作不该藏在统计页里）。
 *
 * 可视化保持墨线语言：除了一根**胜率墨条**（按胜率填墨）之外全是文字，
 * 数字一律走数字族（Nunito）——位数不同 / 刷新时不会左右晃。
 */
@Composable
fun ColumnScope.StatsSheet(stats: GameStats, strings: Strings) {
    InkText(
        text = strings.statsTitle,
        modifier = Modifier.fillMaxWidth(),
        style = Ink.Type.Title.copy(color = Ink.Black),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(DesignTokens.Spacing.Md))

    if (stats.gamesTotal == 0) {
        InkText(
            text = strings.statsEmpty,
            modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.Spacing.Lg),
            style = Ink.Type.Body.copy(color = Ink.Grey),
            textAlign = TextAlign.Center,
        )
        return
    }

    StatRow(strings.statsGames, stats.gamesWon.toString())
    StatRow(strings.statsAbandoned, stats.gamesAbandoned.toString())
    val ratePct = (stats.winRate * 100).roundToInt()
    StatRow(strings.statsWinRate, "$ratePct%")
    WinRateBar(ratePct)

    Spacer(Modifier.height(DesignTokens.Spacing.Md))
    StatRow(strings.statsStreak, stats.currentStreak.toString())
    StatRow(strings.statsBestStreak, stats.bestStreak.toString())
    StatRow(strings.statsTotalTime, Sudoku.formatDuration(stats.totalPlaySeconds))
    StatRow(strings.statsMoves, stats.totalMoves.toString())
    StatRow(strings.statsAvgPerMove, formatSeconds(stats.avgSecondsPerMove, strings.secondsUnit))
    StatRow(strings.statsHintsCount, stats.totalHints.toString())

    Spacer(Modifier.height(DesignTokens.Spacing.Md))
    Difficulty.entries.forEach { difficulty ->
        val best = stats.fastest(difficulty)
        StatRow(
            strings.statsFastest.format(strings.difficulty(difficulty)),
            if (best == 0) "—" else Sudoku.formatDuration(best),
        )
    }
}

/** 一行"标签 —— 数字"。标签用正文，数字走数字族。 */
@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkText(text = label, style = Ink.Type.Body.copy(color = Ink.Grey))
        InkText(text = value, style = Ink.digitStyle(Ink.Type.Body.fontSize, color = Ink.Black))
    }
}

/** 胜率墨条：淡墨框 + 按胜率填墨。全自绘，与棋盘同一支笔。 */
@Composable
private fun WinRateBar(percent: Int) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DesignTokens.Spacing.Xs)
            .height(10.dp),
    ) {
        val rect = Rect(1f, 1f, size.width - 1f, size.height - 1f)
        inkRoundRect(
            rect = rect,
            radiusPx = rect.height / 2f,
            widthPx = DesignTokens.Stroke.Thin.toPx(),
            color = Ink.Black,
            seed = 21,
            alpha = Ink.Alpha.LineSoft,
        )
        // 填墨部分：从左往右按胜率铺（最短画一条细线，避免 0% 时什么都看不见）
        val fillW = rect.width * percent / 100f
        if (fillW > 1f) {
            drawRoundRect(
                color = Ink.Black.copy(alpha = Ink.Alpha.Wash * 2f),
                topLeft = Offset(rect.left + 1f, rect.top + 2f),
                size = Size(fillW - 2f, rect.height - 4f),
                cornerRadius = CornerRadius(rect.height / 2f),
            )
        }
    }
}

/** 平均每步耗时：一位小数 + 单位（公共标准库没有 String.format，手动拼）。 */
private fun formatSeconds(value: Double, unit: String): String {
    val whole = value.toInt()
    val tenth = ((value - whole) * 10).roundToInt().coerceIn(0, 9)
    return "$whole.$tenth $unit"
}
