package org.example.sudoku.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink
import org.example.sudoku.ui.theme.inkLine
import org.example.sudoku.ui.theme.inkRoundRect

/** 墨字：唯一允许的文本出口，保证全站字体与墨色一致。 */
@Composable
fun InkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Ink.style(16.sp),
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(textAlign = textAlign),
        maxLines = maxLines,
    )
}

/**
 * 墨线交互面：所有可点控件的统一底子（按钮 / 数字键 / 菜单项）。
 *
 * 五态反馈全站一致：
 * - 常态：手绘墨框（[emphasis] 重墨、[soft] 淡墨、其余中墨）
 * - 悬停：极淡墨洗（桌面端）
 * - 按压：墨洗加深 + 下移 1dp
 * - 焦点：内侧再加一圈细描框（键盘 Tab 可达）
 * - 禁用：虚线框 + 淡墨字
 */
@Composable
private fun InkSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    seed: Int,
    height: Dp,
    enabled: Boolean = true,
    emphasis: Boolean = false,
    soft: Boolean = false,
    radius: Dp = DesignTokens.Radius.Button,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }

    val washTarget = when {
        !enabled -> 0f
        pressed -> Ink.Alpha.WashStrong + 0.03f
        hovered -> Ink.Alpha.Wash
        focused -> Ink.Alpha.Wash * 0.7f
        else -> 0f
    }
    val wash by animateFloatAsState(washTarget, animationSpec = tween(90), label = "inkWash")
    val pressShift by animateFloatAsState(if (pressed && enabled) 1f else 0f, tween(90), label = "inkPress")

    val lineAlpha = when {
        !enabled -> Ink.Alpha.Disabled
        emphasis -> Ink.Alpha.Line
        soft -> Ink.Alpha.LineSoft
        else -> Ink.Alpha.Line * 0.76f
    }

    Box(
        modifier = modifier
            .height(height)
            .offset(y = pressShift.dp)
            .hoverable(interaction, enabled = enabled)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .drawBehind {
                val rect = Rect(1.5f, 1.5f, size.width - 1.5f, size.height - 1.5f)
                val radiusPx = radius.toPx()
                if (wash > 0f) {
                    drawRoundRect(
                        color = Ink.Black.copy(alpha = wash),
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                        cornerRadius = CornerRadius(radiusPx),
                    )
                }
                inkRoundRect(
                    rect = rect,
                    radiusPx = radiusPx,
                    widthPx = (if (emphasis) DesignTokens.Stroke.Bold else DesignTokens.Stroke.Thin).toPx(),
                    color = Ink.Black,
                    seed = seed,
                    alpha = lineAlpha,
                    dashed = !enabled,
                )
                if (focused && enabled) {
                    val inset = 4.dp.toPx()
                    inkRoundRect(
                        rect = Rect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
                        radiusPx = radiusPx,
                        widthPx = DesignTokens.Stroke.Hair.toPx(),
                        color = Ink.Black,
                        seed = seed + 11,
                        alpha = Ink.Alpha.Hair,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 主 / 次操作按钮（首页菜单项、对局控制条）。 */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    compact: Boolean = false,
) {
    InkSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        seed = remember(text, emphasized) { text.hashCode() * 31 + if (emphasized) 7 else 3 },
        height = if (compact) DesignTokens.Sizes.CompactItemHeight else DesignTokens.Sizes.MenuItemHeight,
        enabled = enabled,
        emphasis = emphasized,
    ) {
        InkText(
            text = text,
            style = Ink.style(
                size = if (emphasized) 21.sp else if (compact) 15.sp else 18.sp,
                color = if (enabled) Ink.Black else Ink.Light,
            ),
        )
    }
}

/** 数字键盘 / 小方块墨键。 */
@Composable
fun InkKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: Boolean = false,
    soft: Boolean = false,
    height: Dp = 52.dp,
    fontSize: TextUnit = 21.sp,
    /** 右上角小角标（数字键盘用它显示"该数字还剩几个"）。 */
    badge: String? = null,
) {
    InkSurface(
        onClick = onClick,
        modifier = modifier,
        seed = remember(text) { text.hashCode() * 17 + 5 },
        height = height,
        enabled = enabled,
        emphasis = emphasis,
        soft = soft,
    ) {
        InkText(
            text = text,
            style = Ink.style(fontSize, if (enabled) Ink.Black else Ink.Light),
        )
        if (badge != null) {
            InkText(
                text = badge,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 7.dp),
                style = Ink.style(10.sp, Ink.Light),
            )
        }
    }
}

/** 墨线开关行：整行可点，方框内用手绘勾（不依赖颜色）。 */
@Composable
fun InkToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seed = remember(label) { label.hashCode() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DesignTokens.Sizes.CompactItemHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkText(text = label, style = Ink.style(16.sp, Ink.Grey))
        Spacer(Modifier.width(DesignTokens.Spacing.Sm))
        Box(
            modifier = Modifier
                .size(24.dp)
                .drawBehind {
                    val rect = Rect(1.5f, 1.5f, size.width - 1.5f, size.height - 1.5f)
                    inkRoundRect(
                        rect = rect,
                        radiusPx = 4.dp.toPx(),
                        widthPx = DesignTokens.Stroke.Thin.toPx(),
                        color = Ink.Black,
                        seed = seed,
                        alpha = if (checked) Ink.Alpha.Line else Ink.Alpha.LineSoft,
                    )
                    if (checked) {
                        inkLine(
                            from = Offset(rect.left + rect.width * 0.20f, rect.top + rect.height * 0.52f),
                            to = Offset(rect.left + rect.width * 0.42f, rect.top + rect.height * 0.78f),
                            widthPx = DesignTokens.Stroke.Bold.toPx(),
                            color = Ink.Black,
                            seed = seed + 1,
                        )
                        inkLine(
                            from = Offset(rect.left + rect.width * 0.40f, rect.top + rect.height * 0.78f),
                            to = Offset(rect.left + rect.width * 0.86f, rect.top + rect.height * 0.18f),
                            widthPx = DesignTokens.Stroke.Bold.toPx(),
                            color = Ink.Black,
                            seed = seed + 2,
                        )
                    }
                },
        )
    }
}

/** 墨线面板：白纸面 + 手绘边框（替代 Material 风格卡片）。 */
@Composable
fun InkPanel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(DesignTokens.Spacing.Lg),
    seed: Int = 1,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val rect = Rect(2f, 2f, size.width - 2f, size.height - 2f)
                val radiusPx = DesignTokens.Radius.Panel.toPx()
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    cornerRadius = CornerRadius(radiusPx),
                )
                inkRoundRect(
                    rect = rect,
                    radiusPx = radiusPx,
                    widthPx = DesignTokens.Stroke.Thin.toPx(),
                    color = Ink.Black,
                    seed = seed,
                    alpha = Ink.Alpha.LineSoft,
                )
            }
            .padding(padding),
        content = content,
    )
}

/** 墨色遮罩层：难度选择 / 退出确认。点空白关闭。 */
@Composable
fun InkOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.Black.copy(alpha = 0.16f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        InkPanel(
            modifier = Modifier
                .padding(DesignTokens.Spacing.Lg)
                .widthIn(max = 440.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { /* 吞掉点击，避免穿透到遮罩 */ },
                ),
            padding = PaddingValues(DesignTokens.Spacing.Lg),
            seed = 21,
            content = content,
        )
    }
}

/**
 * 标题墨框：呼应参考图的"带框标题"，但只用墨线与留白表达。
 * 双层手绘矩形 + 两侧墨点与短竖线装饰。
 */
@Composable
fun InkTitleFrame(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val seed = remember(title) { title.hashCode() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val outer = Rect(2f, 2f, size.width - 2f, size.height - 2f)
                val inset = 7.dp.toPx()
                inkRoundRect(
                    rect = outer,
                    radiusPx = DesignTokens.Radius.Panel.toPx(),
                    widthPx = DesignTokens.Stroke.Frame.toPx(),
                    color = Ink.Black,
                    seed = seed,
                    alpha = Ink.Alpha.Line,
                )
                inkRoundRect(
                    rect = Rect(outer.left + inset, outer.top + inset, outer.right - inset, outer.bottom - inset),
                    radiusPx = DesignTokens.Radius.Button.toPx(),
                    widthPx = DesignTokens.Stroke.Hair.toPx(),
                    color = Ink.Black,
                    seed = seed + 5,
                    alpha = Ink.Alpha.LineSoft,
                )
                val cy = size.height / 2f
                listOf(outer.left + inset * 2.0f, outer.right - inset * 2.0f).forEachIndexed { i, x ->
                    drawCircle(Ink.Black.copy(alpha = Ink.Alpha.LineSoft), radius = 2.2f, center = Offset(x, cy - 1f))
                    drawCircle(Ink.Black.copy(alpha = Ink.Alpha.Hair), radius = 3.4f, center = Offset(x, cy + 5f))
                    inkLine(
                        from = Offset(x, cy - 10.dp.toPx()),
                        to = Offset(x, cy + 12.dp.toPx()),
                        widthPx = DesignTokens.Stroke.Hair.toPx(),
                        color = Ink.Black,
                        seed = seed + 20 + i,
                        alpha = Ink.Alpha.Hair,
                    )
                }
            }
            .padding(horizontal = 46.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            InkText(
                text = title,
                style = Ink.style(40.sp, Ink.Black, letterSpacing = 6.sp),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                InkText(text = subtitle, style = Ink.style(13.sp, Ink.Light, letterSpacing = 3.sp))
            }
        }
    }
}

/** 手绘分隔线（面板内分组）。 */
@Composable
fun InkDivider(modifier: Modifier = Modifier, seed: Int = 0) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.2.dp)
            .drawBehind {
                inkLine(
                    from = Offset(0f, size.height / 2f),
                    to = Offset(size.width, size.height / 2f),
                    widthPx = DesignTokens.Stroke.Hair.toPx(),
                    color = Ink.Black,
                    seed = seed,
                    alpha = Ink.Alpha.Hair,
                )
            },
    )
}

/** 装饰用 9×9 手绘棋盘草图（首页底部，呼应参考图的棋盘插图）。 */
@Composable
fun InkGridSketch(
    modifier: Modifier = Modifier,
    side: Dp = DesignTokens.Sizes.SketchSize,
    seed: Int = 99,
) {
    Box(modifier = modifier.size(side)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pad = 2f
            val cell = (size.width - pad * 2) / 9f
            val outer = Rect(pad, pad, size.width - pad, size.height - pad)
            for (i in 1..8) {
                val bold = i % 3 == 0
                val w = if (bold) DesignTokens.Stroke.Bold.toPx() else DesignTokens.Stroke.Hair.toPx()
                val a = if (bold) Ink.Alpha.LineSoft else Ink.Alpha.Hair
                val p = pad + i * cell
                inkLine(Offset(p, pad), Offset(p, pad + cell * 9), w, Ink.Black, seed + i * 2, a)
                inkLine(Offset(pad, p), Offset(pad + cell * 9, p), w, Ink.Black, seed + i * 2 + 1, a)
            }
            inkRoundRect(
                rect = outer,
                radiusPx = DesignTokens.Radius.Cell.toPx(),
                widthPx = DesignTokens.Stroke.Frame.toPx(),
                color = Ink.Black,
                seed = seed,
                alpha = Ink.Alpha.LineSoft,
            )
            // 几枚"随手点下的墨迹"，让空网格有生气
            val dots = listOf(1 to 2, 3 to 5, 5 to 1, 6 to 7, 8 to 4)
            dots.forEachIndexed { i, (r, c) ->
                drawCircle(
                    color = Ink.Black.copy(alpha = 0.20f + 0.06f * (i % 3)),
                    radius = cell * (0.20f + 0.03f * (i % 2)),
                    center = Offset(pad + (c + 0.5f) * cell, pad + (r + 0.5f) * cell),
                )
            }
        }
    }
}
