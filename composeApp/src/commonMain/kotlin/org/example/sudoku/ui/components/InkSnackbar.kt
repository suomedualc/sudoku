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
 * 只保留真实用到的两个语义，其余（可撤销 / 自定义配色 / 多条堆叠）一概不做：
 * - [showSnackbar] **挂起**到该条消失：调用方逐条 collect 时自然排队，后一条不会盖掉前一条；
 * - 过期的提示**只清自己**，期间若有新提示顶上，不会被前一条的计时清掉。
 */
class InkSnackbarHostState {
    private var nextId = 0

    /** 当前提示（null = 当前无提示）。 */
    var current: InkSnackbarMessage? by mutableStateOf(null)
        private set

    suspend fun showSnackbar(message: String, durationMs: Long = ShortMs) {
        val id = ++nextId
        current = InkSnackbarMessage(message, id)
        delay(durationMs)
        if (current?.id == id) current = null
    }

    companion object {
        /** 短提示的默认停留时长（毫秒）。 */
        const val ShortMs: Long = 2400L
    }
}

/**
 * 自绘墨条提示的挂载点：盖在页面底部，**不拦截任何点击**（没有 `clickable` / `pointerInput`）。
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
        if (message != null) shown = message
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
