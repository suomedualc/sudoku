package org.example.sudoku.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink
import org.example.sudoku.ui.theme.inkLine
import org.example.sudoku.ui.theme.inkRoundRect
import org.example.sudoku.ui.theme.motionDurationMs
import org.example.sudoku.ui.theme.prefersReducedMotion

/** 墨字：唯一允许的文本出口，保证全站字体与墨色一致。 */
@Composable
fun InkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Ink.Type.Body,
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
    /** 由外部（首页键盘导航）指定的"当前项"高亮：与焦点态共用同一圈墨线。 */
    highlighted: Boolean = false,
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
        // "当前项"与悬停同浓度：键盘走查时必须看得出焦点在哪一项（此前比悬停还淡，等于没有高亮）
        highlighted -> Ink.Alpha.Wash
        else -> 0f
    }
    val wash by animateFloatAsState(
        washTarget,
        animationSpec = tween(motionDurationMs(DesignTokens.Motion.PressMs)),
        label = "inkWash",
    )
    val pressShift by animateFloatAsState(
        if (pressed && enabled) 1f else 0f,
        tween(motionDurationMs(DesignTokens.Motion.PressMs)),
        label = "inkPress",
    )

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
                // "当前项"与"重墨"同线宽：只靠一层淡底纹在整屏缩放下根本看不出选中项
                inkRoundRect(
                    rect = rect,
                    radiusPx = radiusPx,
                    widthPx = (if (emphasis || highlighted) DesignTokens.Stroke.Bold else DesignTokens.Stroke.Thin).toPx(),
                    color = Ink.Black,
                    seed = seed,
                    alpha = lineAlpha,
                    dashed = !enabled,
                )
                if ((focused || highlighted) && enabled) {
                    val inset = DesignTokens.Spacing.Xs.toPx()
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

/** 主 / 次操作按钮（首页菜单项、对局控制条、墨框按钮）。 */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    compact: Boolean = false,
    highlighted: Boolean = false,
) {
    InkSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        seed = remember(text, emphasized) { text.hashCode() * 31 + if (emphasized) 7 else 3 },
        height = if (compact) DesignTokens.Sizes.CompactItemHeight else DesignTokens.Sizes.MenuItemHeight,
        enabled = enabled,
        emphasis = emphasized,
        highlighted = highlighted,
    ) {
        InkText(
            text = text,
            style = (if (emphasized) Ink.Type.Title else if (compact) Ink.Type.Caption else Ink.Type.Body)
                .copy(color = if (enabled) Ink.Black else Ink.Faint),
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
    height: Dp = DesignTokens.Sizes.KeyHeight,
    fontSize: TextUnit = Ink.Type.Title.fontSize,
    /** 右上角小角标（数字键盘用它显示"该数字还剩几个"）。 */
    badge: String? = null,
    /** 内容是纯数字时置 true：换 [Ink.FontDigits]（Nunito）并收紧字距——数字不是方块字，不需要标题那 2sp 的呼吸字距。 */
    digit: Boolean = false,
) {
    val keyStyle = if (digit) {
        Ink.Type.Title.copy(fontFamily = Ink.FontDigits, letterSpacing = 0.sp)
    } else {
        Ink.Type.Title
    }
    val badgeStyle = if (digit) {
        Ink.Type.Meta.copy(color = Ink.Light, fontFamily = Ink.FontDigits, letterSpacing = 0.sp)
    } else {
        Ink.Type.Meta.copy(color = Ink.Light)
    }
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
            style = keyStyle.copy(fontSize = fontSize, color = if (enabled) Ink.Black else Ink.Faint),
        )
        if (badge != null) {
            InkText(
                text = badge,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = DesignTokens.Spacing.Xs, end = DesignTokens.Spacing.Sm),
                style = badgeStyle,
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
        InkText(text = label, style = Ink.Type.Body.copy(color = Ink.Grey))
        Spacer(Modifier.width(DesignTokens.Spacing.Sm))
        Box(
            modifier = Modifier
                .size(DesignTokens.Sizes.MarkBox)
                .drawBehind {
                    val rect = Rect(1.5f, 1.5f, size.width - 1.5f, size.height - 1.5f)
                    inkRoundRect(
                        rect = rect,
                        radiusPx = DesignTokens.Radius.Mark.toPx(),
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

/**
 * 墨线面板：白纸面 + 手绘边框（替代 Material 风格卡片）。
 *
 * - [paperAlpha] < 1 时纸面半透明：用于**悬浮数字面板**，让底下的网格隐约可见（读作"浮着的一层薄纸"）；
 * - [doubleStroke] 为真时再叠一圈错位淡墨描边，用**线**表达"浮起"（墨线语言里没有阴影）；
 * - [fillWidth] 默认撑满可用宽（正常的面板）。**浮动操作条**（撤销 / 重做）要传 `false`：
 *   它只有两个按钮，撑满整宽会变成一条横贯棋盘的空白带，既压不住视觉重心、又抢棋盘的注意力。
 */
@Composable
fun InkPanel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(DesignTokens.Spacing.Lg),
    seed: Int = 1,
    paperAlpha: Float = 1f,
    doubleStroke: Boolean = false,
    fillWidth: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .drawBehind {
                val rect = Rect(2f, 2f, size.width - 2f, size.height - 2f)
                val radiusPx = DesignTokens.Radius.Panel.toPx()
                if (doubleStroke) {
                    inkRoundRect(
                        rect = Rect(rect.left - 2.5f, rect.top - 2.5f, rect.right - 0.5f, rect.bottom - 0.5f),
                        radiusPx = radiusPx + 1.5f,
                        widthPx = DesignTokens.Stroke.Hair.toPx(),
                        color = Ink.Black,
                        seed = seed + 3,
                        alpha = Ink.Alpha.Hair,
                    )
                }
                drawRoundRect(
                    color = Ink.PaperSheet.copy(alpha = paperAlpha),
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
                val inset = DesignTokens.Spacing.Sm.toPx()
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
                        from = Offset(x, cy - DesignTokens.Spacing.Sm.toPx()),
                        to = Offset(x, cy + DesignTokens.Spacing.Md.toPx()),
                        widthPx = DesignTokens.Stroke.Hair.toPx(),
                        color = Ink.Black,
                        seed = seed + 20 + i,
                        alpha = Ink.Alpha.Hair,
                    )
                }
            }
            .padding(horizontal = DesignTokens.Spacing.Xxl, vertical = DesignTokens.Spacing.Md),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            InkText(text = title, style = Ink.Type.Display)
            if (subtitle != null) {
                Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                InkText(text = subtitle, style = Ink.Type.Caption.copy(color = Ink.Light))
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
            .height(DesignTokens.Stroke.Thin)
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
                    color = Ink.Black.copy(alpha = Ink.Alpha.Decor + Ink.Alpha.DecorStep * (i % 3)),
                    radius = cell * (0.20f + 0.03f * (i % 2)),
                    center = Offset(pad + (c + 0.5f) * cell, pad + (r + 0.5f) * cell),
                )
            }
        }
    }
}

// ───────────────────────────── 自绘图标 ─────────────────────────────

/**
 * 图标形状：全部用 [inkLine] 与弧线**自绘**，不引入图标库、不用图标字体。
 *
 * 两个理由：① 图标必须与墨线控件同一套笔触（微弯 + 叠墨），位图与字体图标在缩放 / 高 DPI 下会露馅，
 * 而且换不出"手绘"的工艺感（`docs/02` §3.2 的"不引入第三方组件库"同一条纪律）；
 * ② 按钮只有 36dp，**每一枚都要"一眼认出功能"**，因此按"骨架差异"而不是"细节差异"来设计：
 *
 * | 图标 | 骨架 | 与同类怎么区分 |
 * |---|---|---|
 * | 返回 | 一根横杆 + 箭尖 | 唯一**水平**的箭头 |
 * | 撤销 / 重做 | 半圈弧 + 箭尖 | 互为镜像（一个过顶向左、一个过顶向右） |
 * | 重置 | **整圈**弧 + 箭尖 | 弧长接近一整圈，与"半圈"的撤销 / 重做一眼分开 |
 * | 暂停 / 继续 | 两条竖杠 / 一个三角 | 互补的一对（同一位置切换，形状必须相反） |
 * | 提示 | **灯泡**（灯罩 + 灯座 + 光线） | 通用符号；比"!"更不容易被误读成"警告" |
 * | 太阳 / 月牙 | 圆 + 光芒 / 缺一段的圆 | 显示的是"点下去会变成什么"，不是当前状态 |
 * | 键位设置 | **键盘**（一个框里排着键） | 键位设置入口的唯一符号；框 + 两排错开的键 + 一条空格键，避免和"格子"混 |
 * | 擦除键位 | **橡皮**（斜置方块 + 套圈 + 两道擦痕） | 比"一个叉"更像"擦掉"，也不会被读成"关闭 / 错误" |
 * | 上 / 下 / 左 / 右 | **单向箭头**（杆 + 箭尖） | 四个方向互为 90° 旋转，形状一致、指向不同——设置抽屉里逐条列出，靠指向区分 |
 */
enum class InkIcon {
    /** ← 返回菜单。 */
    Back,

    /** ↶ 撤销。 */
    Undo,

    /** ↷ 重做。 */
    Redo,

    /** ‖ 暂停。 */
    Pause,

    /** ▶ 继续。 */
    Play,

    /** ↺ 重置本局。 */
    Reset,

    /** ！ 提示（求一个候选数字）。 */
    Hint,

    /** 月牙：夜墨模式。 */
    Moon,

    /** 太阳：浅色纸面。 */
    Sun,

    /** ⌨ 键位设置（键盘）。 */
    Keyboard,

    /** 橡皮：擦除键位。 */
    Eraser,

    /** ↑ 向上键位。 */
    ArrowUp,

    /** ↓ 向下键位。 */
    ArrowDown,

    /** ← 向左键位。 */
    ArrowLeft,

    /** → 向右键位。 */
    ArrowRight,
}

/**
 * 非交互的图标（列表 / 说明里的"图例"用）：**只画形状**，不参与点击与五态反馈。
 *
 * 与 [InkIconButton] 的分工：按钮要五态与读屏文案，图例只要"一眼认出这是什么"——
 * 后者不该被当成可点的东西（`InkIconButton` 的 `enabled = false` 会把图标画成淡墨，正是这里不想要的）。
 */
@Composable
fun InkIconGlyph(
    icon: InkIcon,
    modifier: Modifier = Modifier,
    tint: Color = Ink.Black,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        drawInkIcon(icon, tint)
    }
}

/**
 * 图标按钮：方形墨线按钮 + 手绘图标，复用 [InkSurface] 的完整五态反馈。
 *
 * 用于对局页顶栏（返回菜单 / 暂停 / 重置 / 明暗切换）与棋盘上下的浮动操作条（撤销 / 重做）。
 * 图标只做形状、文字说明走 `contentDescription`——读屏读的是"撤销"，不是"一个圆圈带箭头"。
 */
@Composable
fun InkIconButton(
    icon: InkIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: Boolean = false,
    size: Dp = DesignTokens.Sizes.IconButton,
    /**
     * 悬停时浮出的功能名。**默认就是 [contentDescription]**——它本来就是人话写的功能名，
     * 再单独造一个字段只会让两者慢慢走偏（按钮读屏念一个词、悬停显示另一个词是最糟的情况）。
     * 需要更短的写法时才显式传（例如"切回浅色纸面"悬停时缩成"纸面模式"）。
     */
    tooltip: String = contentDescription,
    /**
     * 提示片相对按钮的对齐（默认贴按钮正下方居中）。**贴窗口边缘的按钮必须显式给方向**，
     * 否则提示片会被边缘切掉：顶栏最右侧的按钮传 `Alignment.TopEnd`（提示片向左长），
     * 最左侧的传 `Alignment.TopStart`。
     * 用"对齐方向"而不是"测量后夹取"：不依赖任何运行时尺寸，也就不会有一帧的错位。
     */
    tooltipAlignment: Alignment = Alignment.TopCenter,
) {
    var hovered by remember { mutableStateOf(false) }
    val reduceMotion = prefersReducedMotion()
    val tipAlpha by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(motionDurationMs(DesignTokens.Motion.EnterMs, reduceMotion)),
        label = "iconTooltip",
    )

    // 外层**固定尺寸**：提示片是超出按钮范围的浮层，若让父容器按内容测量，
    // 悬停时按钮就会把顶栏撑高——那正是"布局跳动比功能重复更伤体验"的典型现场。
    Box(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // 只观察进出，不消费：按钮自己的 clickable 照常工作
                        hovered = when (event.type) {
                            PointerEventType.Enter -> true
                            PointerEventType.Exit -> false
                            else -> hovered
                        }
                    }
                }
            },
    ) {
        InkSurface(
            onClick = onClick,
            modifier = Modifier.size(size).semantics { this.contentDescription = contentDescription },
            seed = remember(icon) { icon.ordinal * 23 + 9 },
            height = size,
            enabled = enabled,
            emphasis = emphasis,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawInkIcon(icon, if (enabled) Ink.Black else Ink.Faint)
            }
        }

        if (tipAlpha > 0f) {
            IconTooltip(
                text = tooltip,
                modifier = Modifier
                    // `unbounded = true` 是关键：按钮只有 36dp，若用普通 align，
                    // 提示片会被**父容器宽度**约束成 36dp——文字被截成"夜…"，
                    // 看起来像被窗口切掉，其实是量测约束太紧。unbounded 允许它超出父容器，
                    // 再按 [tooltipAlignment] 决定往哪边长（因此不会溢出窗口）。
                    .wrapContentSize(tooltipAlignment, unbounded = true)
                    .offset(y = size + DesignTokens.Spacing.Xs)
                    .alpha(tipAlpha),
            )
        }
    }
}

/**
 * 图标按钮的悬停提示：一张小纸片 + 手绘墨框 + [Ink.Type.Meta] 文字。
 *
 * 固定在按钮**正下方**：顶栏按钮下方是棋盘上方的留白，浮动条按钮下方是空白纸面，两边都不压内容。
 * 它不带 `clickable`，因此**不拦点击**——鼠标移过去时不会挡住底下的东西。
 */
@Composable
private fun IconTooltip(text: String, modifier: Modifier = Modifier) {
    val seed = remember(text) { text.hashCode() }
    Box(
        modifier = modifier
            .drawBehind {
                val rect = Rect(1f, 1f, size.width - 1f, size.height - 1f)
                drawRoundRect(
                    color = Ink.PaperSheet,
                    topLeft = rect.topLeft,
                    size = Size(rect.width, rect.height),
                    cornerRadius = CornerRadius(DesignTokens.Radius.Mark.toPx()),
                )
                inkRoundRect(
                    rect = rect,
                    radiusPx = DesignTokens.Radius.Mark.toPx(),
                    widthPx = DesignTokens.Stroke.Hair.toPx(),
                    color = Ink.Black,
                    seed = seed,
                    alpha = Ink.Alpha.LineSoft,
                )
            }
            .padding(horizontal = DesignTokens.Spacing.Sm, vertical = DesignTokens.Spacing.Xs),
        contentAlignment = Alignment.Center,
    ) {
        InkText(text = text, style = Ink.Type.Meta.copy(color = Ink.Grey))
    }
}

/**
 * 画图标：所有坐标都按 [DrawScope.size] 归一化，因此换尺寸不用改路径。
 * 线宽也跟着尺寸走，小按钮上不会糊成一坨。
 */
private fun DrawScope.drawInkIcon(icon: InkIcon, color: Color) {
    val s = size.minDimension
    val c = center
    val r = s * 0.26f
    val w = (s * 0.07f).coerceAtLeast(1.1f)
    val a = Ink.Alpha.Line

    when (icon) {
        InkIcon.Back -> {
            inkLine(Offset(c.x + r, c.y), Offset(c.x - r, c.y), w, color, 1, a)
            inkLine(Offset(c.x - r, c.y), Offset(c.x - r * 0.15f, c.y - r * 0.85f), w, color, 2, a)
            inkLine(Offset(c.x - r, c.y), Offset(c.x - r * 0.15f, c.y + r * 0.85f), w, color, 3, a)
        }

        // 撤销 / 重做：**互为镜像的半圈弧**（一个过顶向左、一个过顶向右），箭尖落在弧的终点。
        // 角度约定：0° 在 3 点方向、正值顺时针（屏幕坐标 y 向下，故 270° 是正上方）。
        InkIcon.Undo -> drawArcArrow(c, r, w, color, startDeg = 0f, sweepDeg = -180f, alpha = a, seed = 4)

        InkIcon.Redo -> drawArcArrow(c, r, w, color, startDeg = 180f, sweepDeg = 180f, alpha = a, seed = 7)

        // 重置：**接近一整圈**（300°）的弧，与"半圈"的撤销 / 重做一眼分开
        InkIcon.Reset -> drawArcArrow(c, r, w, color, startDeg = 300f, sweepDeg = -300f, alpha = a, seed = 10)

        InkIcon.Pause -> {
            inkLine(Offset(c.x - r * 0.45f, c.y - r * 0.85f), Offset(c.x - r * 0.45f, c.y + r * 0.85f), w, color, 5, a)
            inkLine(Offset(c.x + r * 0.45f, c.y - r * 0.85f), Offset(c.x + r * 0.45f, c.y + r * 0.85f), w, color, 6, a)
        }

        InkIcon.Play -> {
            val p1 = Offset(c.x - r * 0.5f, c.y - r * 0.85f)
            val p2 = Offset(c.x - r * 0.5f, c.y + r * 0.85f)
            val p3 = Offset(c.x + r * 0.9f, c.y)
            inkLine(p1, p2, w, color, 8, a)
            inkLine(p2, p3, w, color, 9, a)
            inkLine(p3, p1, w, color, 11, a)
        }

        InkIcon.Hint -> drawBulb(c, r, w, color, a)

        InkIcon.Sun -> {
            drawCircle(color.copy(alpha = a), radius = r * 0.5f, center = c, style = Stroke(width = w, cap = StrokeCap.Round))
            repeat(8) { i ->
                val rad = i * PI.toFloat() / 4f
                val dir = Offset(cos(rad), sin(rad))
                inkLine(c + dir * (r * 0.78f), c + dir * (r * 1.15f), w, color, 12 + i, a)
            }
        }

        InkIcon.Moon -> {
            // 月牙 = 外弧（缺一段的圆）接内弧（偏移的圆）再闭合，一次成形的双弧路径
            val path = Path().apply {
                arcTo(Rect(c.x - r, c.y - r, c.x + r, c.y + r), 60f, 250f, true)
                arcTo(Rect(c.x - r * 0.9f, c.y - r * 1.05f, c.x + r * 1.1f, c.y + r * 0.95f), 310f, -250f, false)
                close()
            }
            drawPath(path, color.copy(alpha = a), style = Stroke(width = w, cap = StrokeCap.Round))
        }

        // 键盘（键位设置）：骨架是"**一个框里排着键**"——框 + 上排四键 / 下排三键**错开**
        // + 一条更长的空格键。错开是关键：对齐成网格就会被读成"表格 / 棋盘"。
        InkIcon.Keyboard -> drawKeyboard(c, r, w, color, a)

        // 橡皮（擦除键位）：斜置的方块 + 一道套圈 + 底下的两道擦痕
        InkIcon.Eraser -> drawEraser(c, r, w, color, a)

        // 四个方向互为 90° 旋转：形状完全一致、只有指向不同，设置抽屉里逐条列出时靠指向区分
        InkIcon.ArrowUp -> drawArrow(c, r, w, color, a, dirX = 0f, dirY = -1f, seed = 80)
        InkIcon.ArrowDown -> drawArrow(c, r, w, color, a, dirX = 0f, dirY = 1f, seed = 84)
        InkIcon.ArrowLeft -> drawArrow(c, r, w, color, a, dirX = -1f, dirY = 0f, seed = 88)
        InkIcon.ArrowRight -> drawArrow(c, r, w, color, a, dirX = 1f, dirY = 0f, seed = 92)
    }
}

/**
 * 单向箭头（四个方向键位共用）：一根杆 + 两条箭尖，靠 [dirX] / [dirY] 旋转。
 *
 * 与「返回」图标刻意不同：返回是**横杆 + 上下两撇**的扁箭头，方向键位是**细长杆 + 收拢的箭尖**——
 * 两者都要在 36dp 里出现，骨架必须分开，否则"返回菜单"和"向左"会混。
 */
private fun DrawScope.drawArrow(
    center: Offset,
    radius: Float,
    width: Float,
    color: Color,
    alpha: Float,
    dirX: Float,
    dirY: Float,
    seed: Int,
) {
    val tip = Offset(center.x + dirX * radius, center.y + dirY * radius)
    val tail = Offset(center.x - dirX * radius, center.y - dirY * radius)
    val normal = Offset(-dirY, dirX) // 垂直于指向，用来张开支箭尖
    inkLine(tail, tip, width, color, seed, alpha)
    val back = Offset(dirX, dirY) * radius * 0.72f
    inkLine(tip, tip - back + normal * (radius * 0.5f), width, color, seed + 1, alpha)
    inkLine(tip, tip - back - normal * (radius * 0.5f), width, color, seed + 2, alpha)
}

/** 键盘：外框 + 两排错开的键 + 一条更长的空格键。 */
private fun DrawScope.drawKeyboard(
    center: Offset,
    radius: Float,
    width: Float,
    color: Color,
    alpha: Float,
) {
    val halfW = radius
    val halfH = radius * 0.82f
    inkRoundRect(
        rect = Rect(center.x - halfW, center.y - halfH, center.x + halfW, center.y + halfH),
        radiusPx = radius * 0.20f,
        widthPx = width,
        color = color,
        seed = 60,
        alpha = alpha,
    )
    val keyW = radius * 0.26f
    // 上排 4 键
    for (i in 0..3) {
        val x = center.x + (i - 1.5f) * radius * 0.5f
        inkLine(
            Offset(x, center.y - halfH * 0.48f),
            Offset(x + keyW, center.y - halfH * 0.48f),
            width * 0.8f,
            color,
            61 + i,
            alpha,
        )
    }
    // 下排 3 键（与上排错开半格，才像键盘而不像表格）
    for (i in 0..2) {
        val x = center.x + (i - 1f) * radius * 0.5f
        inkLine(
            Offset(x, center.y - halfH * 0.02f),
            Offset(x + keyW, center.y - halfH * 0.02f),
            width * 0.8f,
            color,
            70 + i,
            alpha,
        )
    }
    // 空格键：明显更长的一条
    inkLine(
        Offset(center.x - radius * 0.5f, center.y + halfH * 0.46f),
        Offset(center.x + radius * 0.5f, center.y + halfH * 0.46f),
        width * 0.8f,
        color,
        74,
        alpha,
    )
}

/**
 * 橡皮（擦除键位）：**斜置**的方块 + 腰部一道套圈 + 右下两道擦痕。
 *
 * 为什么斜置：正放的方块会被读成"按钮"或"格子"（这两者画面里都有）；
 * 斜着放才读作"一块橡皮横躺着擦过纸面"。擦痕是它的功能痕迹——没有擦痕就只是个方块。
 */
private fun DrawScope.drawEraser(
    center: Offset,
    radius: Float,
    width: Float,
    color: Color,
    alpha: Float,
) {
    val deg = -22f * PI.toFloat() / 180f
    val u = Offset(cos(deg), sin(deg)) // 长轴
    val v = Offset(-u.y, u.x) // 短轴（法向）
    val a = radius * 0.80f
    val b = radius * 0.38f
    fun corner(su: Float, sv: Float): Offset = center + u * (a * su) + v * (b * sv)

    val p1 = corner(-1f, -1f)
    val p2 = corner(1f, -1f)
    val p3 = corner(1f, 1f)
    val p4 = corner(-1f, 1f)
    inkLine(p1, p2, width, color, 30, alpha)
    inkLine(p2, p3, width, color, 31, alpha)
    inkLine(p3, p4, width, color, 32, alpha)
    inkLine(p4, p1, width, color, 33, alpha)
    // 套圈（橡皮中间那道分界）
    val midU = 0.25f
    inkLine(corner(midU, -1f), corner(midU, 1f), width * 0.85f, color, 34, alpha)
    // 两道擦痕：在橡皮右下方，短、斜、不与本体相撞
    inkLine(
        Offset(center.x + radius * 0.20f, center.y + radius * 0.72f),
        Offset(center.x + radius * 0.72f, center.y + radius * 0.62f),
        width * 0.8f,
        color,
        35,
        alpha,
    )
    inkLine(
        Offset(center.x + radius * 0.28f, center.y + radius * 0.98f),
        Offset(center.x + radius * 0.80f, center.y + radius * 0.88f),
        width * 0.8f,
        color,
        36,
        alpha,
    )
}

/**
 * 灯泡（"提示"）：灯罩是一个圆，灯座是两条短横加两根短竖，顶上三道短光线。
 *
 * 为什么不用"!"：在只有纸与墨的画面上，"!"太容易被读成**警告 / 错误**，而它的实际功能是"给我一个提示"
 * ——灯泡是这件事的通用符号。代价是笔画多一点，但 36dp 上仍然分得清（灯罩占了一半高度）。
 */
private fun DrawScope.drawBulb(
    center: Offset,
    radius: Float,
    width: Float,
    color: Color,
    alpha: Float,
) {
    val r = radius
    val bulbR = r * 0.50f
    val bulbC = Offset(center.x, center.y - r * 0.20f)
    val baseY = center.y + r * 0.46f
    val thin = width * 0.85f

    // 三道光线（先画，被灯罩压住也无所谓——都在灯罩外）
    for ((i, deg) in listOf(-140f, -90f, -40f).withIndex()) {
        val rad = deg * PI.toFloat() / 180f
        val dir = Offset(cos(rad), sin(rad))
        inkLine(
            from = bulbC + dir * (bulbR + width * 1.4f),
            to = bulbC + dir * (bulbR + width * 3.2f),
            widthPx = thin,
            color = color,
            seed = 40 + i,
            alpha = alpha,
        )
    }
    // 灯罩
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = bulbR,
        center = bulbC,
        style = Stroke(width = width, cap = StrokeCap.Round),
    )
    // 灯座：上下各一条短横，两侧各一根短竖把它们接上灯罩
    inkLine(Offset(center.x - bulbR * 0.62f, center.y + r * 0.16f), Offset(center.x - bulbR * 0.62f, baseY), thin, color, 44, alpha)
    inkLine(Offset(center.x + bulbR * 0.62f, center.y + r * 0.16f), Offset(center.x + bulbR * 0.62f, baseY), thin, color, 45, alpha)
    inkLine(Offset(center.x - bulbR * 0.78f, baseY), Offset(center.x + bulbR * 0.78f, baseY), width, color, 46, alpha)
    inkLine(
        Offset(center.x - bulbR * 0.42f, center.y + r * 0.78f),
        Offset(center.x + bulbR * 0.42f, center.y + r * 0.78f),
        width,
        color,
        47,
        alpha,
    )
}

/**
 * 带箭头的圆弧（撤销 / 重做 / 重置只差角度参数）。
 *
 * [startDeg] / [sweepDeg] 与 `drawArc` 同义（0° 在 3 点方向、正值顺时针）。
 * 箭头画在**弧的终点**，朝向由该点切线决定：顺时针 `+90°`、逆时针 `-90°`。
 */
private fun DrawScope.drawArcArrow(
    center: Offset,
    radius: Float,
    width: Float,
    color: Color,
    startDeg: Float,
    sweepDeg: Float,
    alpha: Float,
    seed: Int,
) {
    val box = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    drawArc(
        color = color.copy(alpha = alpha),
        startAngle = startDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = box.topLeft,
        size = Size(box.width, box.height),
        style = Stroke(width = width, cap = StrokeCap.Round),
    )
    val endDeg = startDeg + sweepDeg
    val rad = endDeg * PI.toFloat() / 180f
    val tip = Offset(center.x + radius * cos(rad), center.y + radius * sin(rad))
    val tangent = endDeg + if (sweepDeg > 0f) 90f else -90f
    val back = tangent + 180f
    for ((i, spread) in listOf(-35f, 35f).withIndex()) {
        val wingRad = (back + spread) * PI.toFloat() / 180f
        inkLine(
            from = tip,
            to = Offset(tip.x + radius * 0.55f * cos(wingRad), tip.y + radius * 0.55f * sin(wingRad)),
            widthPx = width,
            color = color,
            seed = seed + i,
            alpha = alpha,
        )
    }
}
