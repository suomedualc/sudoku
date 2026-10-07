package org.example.sudoku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import org.example.sudoku.state.GameAction
import org.example.sudoku.state.GameState
import org.example.sudoku.ui.i18n.Strings
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink
import kotlin.math.roundToInt

/** 悬浮面板落点（像素，相对棋盘左上角）。 */
data class PadPlacement(val x: Float, val y: Float)

/**
 * 悬浮数字面板的**纯逻辑**：位置计算、尺寸、开合判据。
 *
 * 单独抽出来是为了能直接单测——"面板会不会跑到棋盘外""还该不该留在屏幕上"这类问题
 * 用肉眼验收成本太高，用断言几行就能覆盖（见 `commonTest/.../ui/FloatingPadPolicyTest.kt`）。
 */
object FloatingPadPolicy {

    /** 面板最多三列（候选数字一排最多放三个）。 */
    const val MAX_COLUMNS = 3

    /** 列数按**候选个数**自适应：1 个候选时面板只有一键宽。 */
    fun columns(digitCount: Int): Int = digitCount.coerceIn(1, MAX_COLUMNS)

    /** 行数（候选为 0 时留一行放"无可填数字"提示）。 */
    fun rows(digitCount: Int): Int {
        val columns = columns(digitCount)
        return ((digitCount + columns - 1) / columns).coerceAtLeast(1)
    }

    fun padWidth(digitCount: Int, keySize: Float, gap: Float, padding: Float): Float {
        val columns = columns(digitCount)
        return padding * 2f + columns * keySize + (columns - 1) * gap
    }

    fun padHeight(digitCount: Int, keySize: Float, gap: Float, padding: Float): Float {
        val rows = rows(digitCount)
        return padding * 2f + rows * keySize + (rows - 1) * gap
    }

    /**
     * 面板贴在选中格**右侧**并与该格垂直居中；右侧放不下就翻到左侧；
     * 两侧都放不下（棋盘极窄）时贴右边缘；纵向始终 clamp 在棋盘内。
     */
    fun placement(
        cell: Int,
        cellSide: Float,
        padWidth: Float,
        padHeight: Float,
        boardSide: Float,
        gap: Float,
    ): PadPlacement {
        val col = cell % 9
        val row = cell / 9
        val cellLeft = col * cellSide
        val cellTop = row * cellSide
        val right = cellLeft + cellSide + gap
        val left = cellLeft - padWidth - gap
        val x = when {
            right + padWidth <= boardSide -> right
            left >= 0f -> left
            else -> (boardSide - padWidth).coerceAtLeast(0f)
        }
        val maxY = (boardSide - padHeight).coerceAtLeast(0f)
        val y = (cellTop + cellSide / 2f - padHeight / 2f).coerceIn(0f, maxY)
        return PadPlacement(x, y)
    }

    /**
     * 这条动作之后面板是否**保持打开**。
     *
     * 只有三类：`Select`（点棋盘选格）、`Move`（方向键换格，面板跟着走）、
     * `SyncElapsed`（每秒心跳，绝不能因此闪掉）。
     * 其余（填数 / 擦除 / 撤销 / 提示 / 暂停 / 切开关 / 回首页 …）都关闭——
     * 那些动作之后面板悬在已经变了的盘面上只会碍事。
     */
    /** 只改"选中哪一格"的动作都算换格（面板跟着走而不是关掉）：Select / Move / 开局定位。 */
    fun keepsOpen(action: GameAction): Boolean =
        action is GameAction.Select ||
            action is GameAction.Move ||
            action is GameAction.FocusFirstEmpty ||
            action is GameAction.SyncElapsed

    /**
     * 点击某格时是否应弹出面板：**精确指针**（鼠标 / 触控笔）+ 棋盘可交互 + 目标是非给定格。
     *
     * 手指（触屏）不弹面板：面板就出现在手指下面，会被完全挡住——触屏走常驻数字键盘（`docs/02` §6.1）。
     */
    fun shouldOpen(state: GameState, cell: Int, precisePointer: Boolean): Boolean {
        if (!precisePointer || !state.interactive) return false
        val game = state.game ?: return false
        return game.current[cell] == 0
    }

    /**
     * 点击某格之后，面板应处于哪一格（`null` = 关闭）。
     *
     * 在 [shouldOpen] 之外多做一件事：**再点一次当前已开面板的格子 → 收起**（toggle）。
     * 否则面板只能靠 `Esc`、或点到棋盘外的面板区才关得掉——但在棋盘上"再点一下"才是
     * 最顺手的收起手势，缺了它就成了交互死角（面板一直在那儿挡着格子）。
     */
    fun nextOnCellClick(
        current: Int?,
        state: GameState,
        cell: Int,
        precisePointer: Boolean,
    ): Int? = if (current == cell) null else if (shouldOpen(state, cell, precisePointer)) cell else null
}

/**
 * 半透明悬浮数字面板（墨线版）：**只列出这一格当前可填的数字**。
 *
 * - **为什么只列可填的**：数独一格往往只有 2–4 个候选，列全 1–9 既占地方又把键挤小；
 *   只列候选 → 键可以做到 48dp（比原来大 26%）、面板整体更小、更贴近手指/指针的落点。
 *   想记"不可能的数字"或擦除，用右侧常驻键盘（它始终在）。
 * - **位置**：`FloatingPadPolicy.placement` 计算，锚在被点格旁，越界自动翻转 / 贴边；
 * - **样式**：纸面 94% 不透明（能透出底下的网格）+ 双层错位描边表达"浮起"，不用阴影；
 * - **不接管焦点**：键盘操作全程可用（见 `docs/02` §4.1）。
 *
 * 生命周期（何时出现 / 何时消失）由 `GameScreen` 持有，这里只负责画。
 */
@Composable
fun FloatingInputPad(
    cell: Int,
    enabled: Boolean,
    /** 字典（"本格无可填数字"提示从这里取）。 */
    strings: Strings,
    legalMask: Int,
    onDigit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val digits = remember(legalMask) { (1..9).filter { (legalMask shr (it - 1)) and 1 == 1 } }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val boardPx = with(density) { minOf(maxWidth, maxHeight).toPx() }
        val keyPx = with(density) { DesignTokens.Sizes.PadKeySize.toPx() }
        val gapPx = with(density) { DesignTokens.Sizes.PadGap.toPx() }
        val paddingPx = with(density) { DesignTokens.Sizes.PadPadding.toPx() }
        val widthPx = FloatingPadPolicy.padWidth(digits.size, keyPx, gapPx, paddingPx)
        val heightPx = FloatingPadPolicy.padHeight(digits.size, keyPx, gapPx, paddingPx)
        val placement = FloatingPadPolicy.placement(
            cell = cell,
            cellSide = boardPx / 9f,
            padWidth = widthPx,
            padHeight = heightPx,
            boardSide = boardPx,
            gap = with(density) { DesignTokens.Sizes.PadAnchorGap.toPx() },
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(placement.x.roundToInt(), placement.y.roundToInt()) }
                .width(with(density) { widthPx.toDp() }),
        ) {
            InkPanel(
                padding = PaddingValues(DesignTokens.Sizes.PadPadding),
                seed = 61,
                paperAlpha = Ink.Alpha.Sheet,
                doubleStroke = true,
            ) {
                if (digits.isEmpty()) {
                    // 极端情况（同一行列宫把 9 个数字都占了）：明说"没有可填"，别让人以为点了没反应
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DesignTokens.Sizes.PadKeySize),
                        contentAlignment = Alignment.Center,
                    ) {
                        InkText(text = strings.padNoCandidates, style = Ink.Type.Meta.copy(color = Ink.Light))
                    }
                } else {
                    digits.chunked(FloatingPadPolicy.MAX_COLUMNS).forEachIndexed { rowIndex, rowDigits ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                DesignTokens.Sizes.PadGap,
                                Alignment.CenterHorizontally,
                            ),
                        ) {
                            rowDigits.forEach { digit ->
                                InkKey(
                                    text = digit.toString(),
                                    onClick = { onDigit(digit) },
                                    modifier = Modifier.width(DesignTokens.Sizes.PadKeySize),
                                    enabled = enabled,
                                    height = DesignTokens.Sizes.PadKeySize,
                                    fontSize = Ink.Type.Title.fontSize,
                                    emphasis = true,
                                )
                            }
                        }
                        if (rowIndex < FloatingPadPolicy.rows(digits.size) - 1) {
                            Spacer(Modifier.height(DesignTokens.Sizes.PadGap))
                        }
                    }
                }
            }
        }
    }
}
