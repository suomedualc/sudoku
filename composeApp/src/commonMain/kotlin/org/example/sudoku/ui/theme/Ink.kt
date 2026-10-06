package org.example.sudoku.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 「手写纸 · 简约油墨」视觉语言的唯一出口。
 *
 * 原则：
 * - **只有纸与墨**：纸 = 暖白，墨 = 近黑；层次只靠墨的浓淡（alpha），不引入色相；
 * - 所有线条用 [inkLine] / [inkRoundRect] 绘制（折线微弯 + 叠一遍淡墨）模拟手绘与笔触不匀；
 * - 抖动由 `seed` 决定的**纯函数**生成——同一根线每次重绘完全一致，不会在重组/重绘时闪动；
 * - 字体走系统手写体（[systemInkFontFamily]），取不到时回退衬线体，绝不因缺字体而崩。
 */
object Ink {
    /** 纸：暖白（刻意极淡，避免彩色观感）。 */
    val Paper = Color(0xFFF7F5F0)
    val PaperShade = Color(0xFFEDE9DF)

    /** 墨：主色 / 次级 / 最淡。 */
    val Black = Color(0xFF1B1A17)
    val Grey = Color(0xFF4B4843)
    val Light = Color(0xFF8B867C)

    /** 墨的浓淡层级（唯一的分层手段）。 */
    object Alpha {
        const val Line = 0.88f
        const val LineSoft = 0.42f
        const val Hair = 0.16f
        const val Wash = 0.07f
        const val WashStrong = 0.14f
        const val Disabled = 0.26f
    }

    /** 线宽（像素值；如需 dp 请用 [DesignTokens] 里的尺寸再 `toPx()`）。 */
    object StrokeWidth {
        const val Hair = 0.8f
        const val Thin = 1.3f
        const val Bold = 2.0f
        const val Frame = 2.4f
    }

    /** 手写体：优先系统硬笔楷书，取不到则回退衬线体。 */
    val InkFont: FontFamily get() = systemInkFontFamily() ?: FontFamily.Serif

    /** 墨字：默认字距略放宽，模拟手写呼吸感。 */
    fun style(
        size: TextUnit,
        color: Color = Black,
        weight: FontWeight = FontWeight.Normal,
        letterSpacing: TextUnit = 0.6.sp,
    ) = TextStyle(
        fontFamily = InkFont,
        fontSize = size,
        color = color,
        fontWeight = weight,
        letterSpacing = letterSpacing,
    )
}

/** 由平台提供手写体（桌面尝试系统中文字体文件；后续平台可改为打包字体）。 */
expect fun systemInkFontFamily(): FontFamily?

/**
 * 稳定伪随机：同一 seed 永远返回同一值，范围 [-0.5, 0.5)。
 * 用它做线条抖动，保证"手绘感"在重绘 / 重组时保持静止。
 */
private fun wobble(seed: Int): Float {
    val x = sin(seed * 12.9898f) * 43758.5453f
    return (x - floor(x)) - 0.5f
}

/** 手绘直线：二次贝塞尔微弯 + 叠一遍淡墨。 */
fun DrawScope.inkLine(
    from: Offset,
    to: Offset,
    widthPx: Float,
    color: Color = Ink.Black,
    seed: Int = 0,
    alpha: Float = Ink.Alpha.Line,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy).coerceAtLeast(0.001f)
    val nx = -dy / length
    val ny = dx / length
    val amp = min(widthPx * 2.2f, 2.6f)

    fun pointAt(t: Float, k: Int): Offset {
        val w = if (t <= 0f || t >= 1f) 0f else wobble(seed + k) * amp
        return Offset(from.x + dx * t + nx * w, from.y + dy * t + ny * w)
    }

    val a = pointAt(0.34f, 1)
    val b = pointAt(0.68f, 2)
    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticBezierTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
        quadraticBezierTo(b.x, b.y, to.x, to.y)
    }
    drawPath(path, color.copy(alpha = alpha), style = Stroke(width = widthPx, cap = StrokeCap.Round))
    translate(0.5f, 0.35f) {
        drawPath(
            path,
            color.copy(alpha = alpha * 0.3f),
            style = Stroke(width = widthPx * 0.55f, cap = StrokeCap.Round),
        )
    }
}

/** 手绘圆角矩形：四边微弯 + 圆角；[dashed] 为真时画虚线（禁用态）。 */
fun DrawScope.inkRoundRect(
    rect: Rect,
    radiusPx: Float,
    widthPx: Float,
    color: Color = Ink.Black,
    seed: Int = 0,
    alpha: Float = Ink.Alpha.Line,
    dashed: Boolean = false,
) {
    val r = min(radiusPx, min(rect.width, rect.height) / 2f)
    val amp = min(widthPx * 1.6f, 2.2f)

    fun bow(a: Offset, b: Offset, k: Int): Offset {
        val mx = (a.x + b.x) / 2f
        val my = (a.y + b.y) / 2f
        val len = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(0.001f)
        val nx = -(b.y - a.y) / len
        val ny = (b.x - a.x) / len
        val w = if (dashed) 0f else wobble(seed + k) * amp
        return Offset(mx + nx * w, my + ny * w)
    }

    val path = Path().apply {
        moveTo(rect.left + r, rect.top)
        val topMid = bow(Offset(rect.left + r, rect.top), Offset(rect.right - r, rect.top), 1)
        quadraticBezierTo(topMid.x, topMid.y, rect.right - r, rect.top)
        quadraticBezierTo(rect.right, rect.top, rect.right, rect.top + r)

        val rightMid = bow(Offset(rect.right, rect.top + r), Offset(rect.right, rect.bottom - r), 2)
        quadraticBezierTo(rightMid.x, rightMid.y, rect.right, rect.bottom - r)
        quadraticBezierTo(rect.right, rect.bottom, rect.right - r, rect.bottom)

        val bottomMid = bow(Offset(rect.right - r, rect.bottom), Offset(rect.left + r, rect.bottom), 3)
        quadraticBezierTo(bottomMid.x, bottomMid.y, rect.left + r, rect.bottom)
        quadraticBezierTo(rect.left, rect.bottom, rect.left, rect.bottom - r)

        val leftMid = bow(Offset(rect.left, rect.bottom - r), Offset(rect.left, rect.top + r), 4)
        quadraticBezierTo(leftMid.x, leftMid.y, rect.left, rect.top + r)
        quadraticBezierTo(rect.left, rect.top, rect.left + r, rect.top)
        close()
    }

    drawPath(
        path,
        color.copy(alpha = alpha),
        style = Stroke(
            width = widthPx,
            cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(widthPx * 3f, widthPx * 3f)) else null,
        ),
    )
    if (!dashed) {
        translate(0.45f, 0.3f) {
            drawPath(
                path,
                color.copy(alpha = alpha * 0.28f),
                style = Stroke(width = widthPx * 0.5f, cap = StrokeCap.Round),
            )
        }
    }
}

/** 斜向排线（冲突格用）：以墨线代替彩色标红。 */
fun DrawScope.inkHatch(
    rect: Rect,
    spacingPx: Float,
    widthPx: Float,
    color: Color = Ink.Black,
    seed: Int = 0,
    alpha: Float = Ink.Alpha.LineSoft,
) {
    val step = max(spacingPx, 3f)
    var i = 0
    var x = rect.left - rect.height
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        while (x < rect.right + rect.height) {
            inkLine(Offset(x, rect.bottom), Offset(x + rect.height, rect.top), widthPx, color, seed + i, alpha)
            x += step
            i++
        }
    }
}
