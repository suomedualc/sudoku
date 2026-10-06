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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import org.example.sudoku.state.GameAction
import org.example.sudoku.state.GameState
import org.example.sudoku.ui.theme.DesignTokens
import kotlin.math.roundToInt

/** 悬浮面板落点（像素，相对棋盘左上角）。 */
data class PadPlacement(val x: Float, val y: Float)

/**
 * 悬浮数字面板的**纯逻辑**：位置计算、开合判据。
 *
 * 单独抽出来是为了能直接单测——"面板会不会跑到棋盘外""按了撤销面板还留着吗"这类问题
 * 用肉眼验收成本太高，用断言几行就能覆盖（见 `commonTest/.../ui/FloatingPadPolicyTest.kt`）。
 */
object FloatingPadPolicy {

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
    fun keepsOpen(action: GameAction): Boolean =
        action is GameAction.Select || action is GameAction.Move || action is GameAction.SyncElapsed

    /**
     * 点击某格时是否应弹出面板：**精确指针**（鼠标 / 触控笔）+ 棋盘可交互 + 目标是非给定格。
     *
     * 手指（触屏）不弹面板：面板就出现在手指下面，会被完全挡住——触屏走常驻数字键盘（`docs/02` §6）。
     */
    fun shouldOpen(state: GameState, cell: Int, precisePointer: Boolean): Boolean {
        if (!precisePointer || !state.interactive) return false
        val game = state.game ?: return false
        return game.current[cell] == 0
    }
}

/**
 * 半透明悬浮数字面板（墨线版）。
 *
 * - **位置**：由 [FloatingPadPolicy.placement] 计算，锚在被点格旁，越界自动翻转 / 贴边；
 * - **样式**：纸面 94% 不透明（能透出底下的网格）+ 双层错位描边表达"浮起"，不用阴影；
 *   与常驻数字键盘同一套按键语言（重墨=可填、淡墨=不可填、禁用=虚线框）；
 * - **不做**：不显示剩余计数角标（132dp 的窄面板里会挤）、不接管焦点（键盘照常工作）。
 *
 * 生命周期（何时出现 / 何时消失）由调用方 [org.example.sudoku.ui.screens.GameScreen] 持有，
 * 这里只负责画。
 */
@Composable
fun FloatingInputPad(
    cell: Int,
    enabled: Boolean,
    legalMask: Int?,
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val side = minOf(maxWidth, maxHeight)
        val boardPx = with(density) { side.toPx() }
        val placement = FloatingPadPolicy.placement(
            cell = cell,
            cellSide = boardPx / 9f,
            padWidth = with(density) { DesignTokens.Sizes.PadWidth.toPx() },
            padHeight = with(density) { DesignTokens.Sizes.PadHeight.toPx() },
            boardSide = boardPx,
            gap = with(density) { DesignTokens.Sizes.PadAnchorGap.toPx() },
        )

        Box(modifier = Modifier.offset { IntOffset(placement.x.roundToInt(), placement.y.roundToInt()) }) {
            InkPanel(
                modifier = Modifier.width(DesignTokens.Sizes.PadWidth),
                padding = PaddingValues(DesignTokens.Sizes.PadPadding),
                seed = 61,
                paperAlpha = 0.94f,
                doubleStroke = true,
            ) {
                repeat(3) { rowIndex ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Sizes.PadGap),
                    ) {
                        repeat(3) { colIndex ->
                            val digit = rowIndex * 3 + colIndex + 1
                            val legal = legalMask == null || (legalMask and (1 shl (digit - 1))) != 0
                            InkKey(
                                text = digit.toString(),
                                onClick = { onDigit(digit) },
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                                height = DesignTokens.Sizes.PadKeyHeight,
                                fontSize = 17.sp,
                                emphasis = legalMask != null && legal,
                                soft = legalMask != null && !legal,
                            )
                        }
                    }
                    if (rowIndex < 2) Spacer(Modifier.height(DesignTokens.Sizes.PadGap))
                }
                Spacer(Modifier.height(DesignTokens.Sizes.PadGap))
                InkKey(
                    text = "擦除",
                    onClick = onErase,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    height = DesignTokens.Sizes.PadEraseHeight,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
