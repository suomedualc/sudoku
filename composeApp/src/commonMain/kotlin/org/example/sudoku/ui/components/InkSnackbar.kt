package org.example.sudoku.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.delay
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink
import org.example.sudoku.ui.theme.inkRoundRect
import org.example.sudoku.ui.theme.motionDurationMs

/**
 * 一条墨条提示。[id] 单调递增——**相同文案也是"新的一条"**，UI 据此重新播一次入场动画。
 *
 * 这一点是刻意保留的语义：早先的提示实现会把等值文案当作同一条吞掉，
 * 导致"再按一次同样报错"时屏幕上毫无反应（`App.kt` 的提示循环里有同样的说明）。
 */
data class InkSnackbarMessage(val text: String, val id: Int)

/**
 * 自绘墨条提示的状态容器。
 *
 * 显示语义是**顶替式**（不排队）：[show] 立即让新提示顶掉当前那条，每条独占
 * [ShortMs] 的固定停留时长——**与点击频率无关**。这是修掉的第二个历史缺陷：
 * 早先"挂起排队"的实现里，连续快速点击（如连按提示键）会让墨条驻留时间随次数累加
 * （N 次 ≈ N × 2.4 秒），看起来像"提示赖着不走"。
 *
 * 只保留真实用到的两个语义，其余（可撤销 / 自定义配色 / 多条堆叠）一概不做：
 * - [show] **不挂起**：调用方（提示事件循环）逐条取到即显示，最新的一条总是可见的；
 * - 到点隐藏由挂载点（[InkSnackbarHost]）的协程守着：它记住自己那条的 [InkSnackbarMessage.id]，
 *   到点只清"自己"（[clearIfCurrent]）——期间若被新提示顶掉，不能误清新提示的显示。
 */
class InkSnackbarHostState {
    private var nextId = 0

    /** 当前提示（null = 当前无提示）。 */
    var current: InkSnackbarMessage? by mutableStateOf(null)
        private set

    /** 立即显示一条提示：顶掉当前那条；相同文案也拿到新 id（重新播入场动画，不会被等值比较吞掉）。 */
    fun show(message: String) {
        current = InkSnackbarMessage(message, ++nextId)
    }

    /** 只清除"还是自己"的提示：id 已被新消息顶掉时不做任何事。 */
    fun clearIfCurrent(id: Int) {
        if (current?.id == id) current = null
    }

    companion object {
        /** 单条提示的固定停留时长（毫秒）：2.4s，落在"2–3 秒"的体验带内；与点击频率无关。 */
        const val ShortMs: Long = 2400L
    }
}

/**
 * 自绘墨条提示的挂载点：盖在页面底部，**不拦截任何点击**（没有 `clickable` / `pointerInput`）。
 *
 * 显示时长的计时在**本组件**的协程里：换一条消息（id 变化）会重启协程、重置计时——
 * 因此无论多快的连续提示，最后一条都恰好停留 [InkSnackbarHostState.ShortMs]。
 *
 * 视觉沿用「手写纸 · 简约油墨」：一张上层纸片 + 手绘墨框，文字走 [Ink.Type.Caption]。
 * 出入场时长一律过 [motionDurationMs]，系统"减少动态效果"开启时瞬间出现。
 */
@Composable
fun InkSnackbarHost(
    state: InkSnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val message = state.current
    // 退场期间 state 已经清空，但文案要留住——否则会先变空、再淡出，看着像闪了一下
    var shown by remember { mutableStateOf<InkSnackbarMessage?>(null) }
    LaunchedEffect(message?.id) {
        val currentMessage = message
        if (currentMessage != null) {
            shown = currentMessage
            // 固定停留时长：到点只清"自己"；期间被新消息顶掉（id 变化）时本协程已被取消
            delay(InkSnackbarHostState.ShortMs)
            state.clearIfCurrent(currentMessage.id)
        }
    }

    val visible = message != null
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = motionDurationMs(
                if (visible) DesignTokens.Motion.EnterMs else DesignTokens.Motion.ExitMs,
            ),
        ),
        label = "inkSnackbar",
    )

    val text = shown?.text ?: return
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        if (progress > 0.01f) {
            Box(
                modifier = Modifier
                    .offset(y = DesignTokens.Spacing.Md * (1f - progress))
                    .alpha(progress)
                    .padding(
                        horizontal = DesignTokens.Spacing.Lg,
                        vertical = DesignTokens.Spacing.Lg,
                    )
                    .drawBehind { drawInkBar(size) }
                    .padding(
                        horizontal = DesignTokens.Spacing.Lg,
                        vertical = DesignTokens.Spacing.Sm,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                InkText(text = text, style = Ink.Type.Caption)
            }
        }
    }
}

/** 墨条本体：上层纸片 + 手绘墨框。 */
private fun DrawScope.drawInkBar(size: Size) {
    val corner = DesignTokens.Radius.Button.toPx()
    drawRoundRect(
        color = Ink.PaperSheet.copy(alpha = Ink.Alpha.Sheet),
        cornerRadius = CornerRadius(corner, corner),
    )
    inkRoundRect(
        rect = Rect(Offset(1f, 1f), Size(size.width - 2f, size.height - 2f)),
        radiusPx = corner,
        widthPx = DesignTokens.Stroke.Thin.toPx(),
        color = Ink.Black,
        seed = 11,
        alpha = Ink.Alpha.Line,
    )
}
