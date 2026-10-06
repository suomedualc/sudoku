package org.example.sudoku.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import org.example.sudoku.core.Game
import org.example.sudoku.core.Sudoku
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink
import org.example.sudoku.ui.theme.inkHatch
import org.example.sudoku.ui.theme.inkLine
import org.example.sudoku.ui.theme.inkRoundRect

/**
 * 棋盘绘制（手写纸 · 油墨版）：只做「从状态画成图」，不含规则。
 *
 * 墨线表达（全部不依赖颜色）：
 * - 单元格层级：冲突（斜排线）> 选中（淡墨 + 手绘双框）> 同值（淡墨）> 同行列宫（极淡墨）
 * - 数字：题目给定 = 实墨；玩家填入 = 淡墨；冲突数字再套一个手绘圈
 * - 笔记：更小的淡墨实心数字，3×3 排布（玩家自己记的）
 * - 候选提示（[hintCandidates] 开启时）：**所有空格**都用更小的**半透明灰**数字直接画出规则允许的候选；
 *   已有笔记的格子显示笔记（那是玩家自己的判断）。同一套 3×3 排布，靠**墨色深浅**区分来源
 * - 选中框：常规模式实线、笔记模式虚线（用线型而不是颜色区分模式）
 * - 尺寸：调用方决定（宽屏取可用高宽的正方形，窄屏占满宽度），本组件保证 1:1
 *
 * @param onCellClick 点击格子：回传格子下标与**是否精确指针**（鼠标 / 触控笔为真，手指为假）。
 *   调用方据此决定要不要弹"悬浮数字面板"——手指会挡住面板，触屏走常驻数字键盘（见 `docs/02` §6）。
 */
@Composable
fun BoardCanvas(
    game: Game,
    selected: Int?,
    notes: IntArray,
    conflicts: BooleanArray,
    noteMode: Boolean,
    showNotes: Boolean = true,
    hintCandidates: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onCellClick: (cell: Int, precisePointer: Boolean) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val onClick by rememberUpdatedState(onCellClick)

    // 跨帧缓存文本测量：键含数字、颜色、字号(px)、字重
    val valueCache = remember { mutableMapOf<InkTextKey, TextLayoutResult>() }
    val noteCache = remember { mutableMapOf<InkTextKey, TextLayoutResult>() }

    // 候选提示：整盘一次算好，只在**盘面变化**时重算（换格 / 选中不动盘面时复用）
    val candidateMasks = remember(game.current) {
        IntArray(81) { pos -> if (game.current[pos] == 0) Sudoku.legalMask(game.current, pos) else 0 }
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                // 自己实现"点击"而不是用 detectTapGestures：需要知道**指针类型**——
                // 鼠标 / 触控笔是精确指针（可以弹悬浮输入面板），手指不是（面板会被手指挡住）。
                // awaitEachGesture + waitForUpOrCancellation 与 detectTapGestures 同源：
                // 移动超出触摸阈值、或被其它手势消费时会返回 null（即不算点击）。
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val precise = down.type != PointerType.Touch
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    val side = minOf(size.width, size.height).toFloat()
                    val cellPx = side / 9f
                    val col = (up.position.x / cellPx).toInt().coerceIn(0, 8)
                    val row = (up.position.y / cellPx).toInt().coerceIn(0, 8)
                    onClick(row * 9 + col, precise)
                }
            },
    ) {
        val cellPx = minOf(size.width, size.height) / 9f
        val selectedValue = selected?.let { game.current[it] } ?: 0

        // 0) 纸面（棋盘用纯白，和页面暖白纸形成极淡层次）
        drawRect(color = Ink.PaperSheet, size = Size(cellPx * 9f, cellPx * 9f))

        fun cellRect(pos: Int) = Rect(
            left = (pos % 9) * cellPx,
            top = (pos / 9) * cellPx,
            right = (pos % 9 + 1) * cellPx,
            bottom = (pos / 9 + 1) * cellPx,
        )

        /** 3×3 小字的落点（笔记与候选提示共用同一套排布）。 */
        fun miniTopLeft(r: Rect, digit: Int): Offset {
            val idx = digit - 1
            return Offset(
                r.left + cellPx * (0.17f + (idx % 3) * 0.29f),
                r.top + cellPx * (0.15f + (idx / 3) * 0.29f),
            )
        }

        // 1) 单元格墨色层次
        for (pos in 0..80) {
            val r = cellRect(pos)
            if (conflicts[pos]) {
                drawRect(
                    color = Ink.Black.copy(alpha = Ink.Alpha.Wash),
                    topLeft = Offset(r.left, r.top),
                    size = Size(r.width, r.height),
                )
                inkHatch(
                    r,
                    spacingPx = cellPx * 0.22f,
                    widthPx = DesignTokens.Stroke.Hatch.toPx(),
                    seed = pos * 7,
                )
                continue
            }
            val isPeer = selected != null && selected != pos &&
                (Sudoku.rowOf(pos) == Sudoku.rowOf(selected) ||
                    Sudoku.colOf(pos) == Sudoku.colOf(selected) ||
                    Sudoku.boxOf(pos) == Sudoku.boxOf(selected))
            val alpha = when {
                pos == selected -> Ink.Alpha.WashStrong
                selectedValue != 0 && game.current[pos] == selectedValue -> Ink.Alpha.Wash
                isPeer -> Ink.Alpha.Hair * 0.55f
                else -> 0f
            }
            if (alpha > 0f) {
                drawRect(
                    color = Ink.Black.copy(alpha = alpha),
                    topLeft = Offset(r.left, r.top),
                    size = Size(r.width, r.height),
                )
            }
        }

        // 2) 格线：细线 + 每 3 格加粗（宫线）
        val thin = DesignTokens.Stroke.Grid.toPx()
        val bold = DesignTokens.Stroke.GridBold.toPx()
        for (i in 1..8) {
            val isBox = i % 3 == 0
            val p = i * cellPx
            val w = if (isBox) bold else thin
            val a = if (isBox) Ink.Alpha.LineSoft else Ink.Alpha.Hair
            inkLine(Offset(p, 0f), Offset(p, cellPx * 9f), w, Ink.Black, seed = i, alpha = a)
            inkLine(Offset(0f, p), Offset(cellPx * 9f, p), w, Ink.Black, seed = 100 + i, alpha = a)
        }
        // 外框：手绘粗框，让整张棋盘像"画"在纸上的表格
        inkRoundRect(
            rect = Rect(1f, 1f, cellPx * 9f - 1f, cellPx * 9f - 1f),
            radiusPx = DesignTokens.Radius.Cell.toPx(),
            widthPx = DesignTokens.Stroke.Frame.toPx(),
            color = Ink.Black,
            seed = 7,
            alpha = Ink.Alpha.Line,
        )

        // 3) 数字与笔记
        for (pos in 0..80) {
            val r = cellRect(pos)
            val value = game.current[pos]
            if (value != 0) {
                val isGiven = Sudoku.isGiven(game, pos)
                val color = if (isGiven) Ink.Black else Ink.Grey
                val fontSizePx = cellPx * 0.52f
                val layout = valueCache.cached(InkTextKey(value, color.value, fontSizePx.toInt(), isGiven)) {
                    textMeasurer.measure(
                        value.toString(),
                        Ink.style(fontSizePx.toSp(), color, if (isGiven) FontWeight.Medium else FontWeight.Normal),
                    )
                }
                val topLeft = Offset(
                    r.left + (cellPx - layout.size.width) / 2f,
                    r.top + (cellPx - layout.size.height) / 2f,
                )
                drawText(layout, topLeft = topLeft)

                // 冲突数字套手绘圈（不靠颜色也能看出来）
                if (conflicts[pos]) {
                    val center = Offset(r.left + cellPx / 2f, r.top + cellPx / 2f)
                    val radius = cellPx * 0.34f
                    val w = DesignTokens.Stroke.Bold.toPx()
                    drawCircle(Ink.Black.copy(alpha = Ink.Alpha.Line), radius, center, style = Stroke(width = w))
                    drawCircle(
                        Ink.Black.copy(alpha = Ink.Alpha.LineSoft),
                        radius + 0.8f,
                        Offset(center.x + 0.6f, center.y - 0.4f),
                        style = Stroke(width = w * 0.5f),
                    )
                }
                continue
            }

            if (showNotes) {
                val mask = notes[pos]
                val fontSizePx = cellPx * 0.24f
                if (mask != 0) {
                    // 玩家自己的笔记优先：那是他写下的判断，不该被系统候选盖住
                    for (digit in 1..9) {
                        if ((mask and (1 shl (digit - 1))) == 0) continue
                        val layout = noteCache.cached(InkTextKey(digit, Ink.Light.value, fontSizePx.toInt(), false)) {
                            textMeasurer.measure(digit.toString(), Ink.style(fontSizePx.toSp(), Ink.Light))
                        }
                        drawText(layout, topLeft = miniTopLeft(r, digit))
                    }
                } else if (hintCandidates) {
                    // 候选提示：所有空格直接写出规则允许的数字，用半透明灰（比笔记更淡，读作"背景信息"）
                    val legal = candidateMasks[pos]
                    if (legal != 0) {
                        for (digit in 1..9) {
                            if ((legal and (1 shl (digit - 1))) == 0) continue
                            val layout = noteCache.cached(InkTextKey(digit, CandidateColor.value, fontSizePx.toInt(), false)) {
                                textMeasurer.measure(
                                    digit.toString(),
                                    Ink.style(fontSizePx.toSp(), CandidateColor),
                                )
                            }
                            drawText(layout, topLeft = miniTopLeft(r, digit))
                        }
                    }
                }
            }
        }

        // 4) 选中框：常规实线 / 笔记模式虚线（线型区分，不用颜色）
        if (selected != null) {
            val r = cellRect(selected)
            val stroke = DesignTokens.Stroke.Selection.toPx()
            val inset = stroke / 2f
            inkRoundRect(
                rect = Rect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset),
                radiusPx = DesignTokens.Radius.Cell.toPx(),
                widthPx = stroke,
                color = Ink.Black,
                seed = selected + 3,
                alpha = Ink.Alpha.Line,
                dashed = noteMode,
            )
            if (!noteMode) {
                inkRoundRect(
                    rect = Rect(
                        r.left + inset + stroke,
                        r.top + inset + stroke,
                        r.right - inset - stroke,
                        r.bottom - inset - stroke,
                    ),
                    radiusPx = DesignTokens.Radius.Cell.toPx(),
                    widthPx = DesignTokens.Stroke.Hair.toPx(),
                    color = Ink.Black,
                    seed = selected + 9,
                    alpha = Ink.Alpha.LineSoft,
                )
            }
        }
    }
}

/** 文本缓存键：数字 + 颜色 + 字号(px) + 是否加粗。 */
private data class InkTextKey(
    val digit: Int,
    val color: ULong,
    val sizePx: Int,
    val bold: Boolean,
)

/**
 * 候选数字的墨色：**半透明灰**。
 * 与玩家笔记（[Ink.Light] 实墨）拉开层次——候选是"系统算出来的背景信息"，笔记是"自己写下的判断"。
 */
private val CandidateColor = Ink.Grey.copy(alpha = Ink.Alpha.Hint)

/** 带容量保护的跨帧文本测量缓存（连续拖拽缩放窗口时不会无限增长）。 */
private fun MutableMap<InkTextKey, TextLayoutResult>.cached(
    key: InkTextKey,
    measure: () -> TextLayoutResult,
): TextLayoutResult {
    if (size > MAX_CACHE_ENTRIES) clear()
    return getOrPut(key, measure)
}

private const val MAX_CACHE_ENTRIES = 256
