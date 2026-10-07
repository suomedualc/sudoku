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
 * - 字体打包分发（[textFontFamily] 霞鹜文楷 / [digitFontFamily] Nunito），取不到时回退系统字体，绝不因缺字体而崩。
 */
object Ink {
    /**
     * 纸：三层，由下往上**逐层变白**，用"纸的深浅"而不是阴影表达层级。
     * - [Paper] 页面底纸（最暖、最暗）；
     * - [PaperShade] 压印 / 底纹（比底纸更暗一档，用于"凹下去"的面）；
     * - [PaperSheet] 上层纸片（棋盘、抽屉、浮层、暂停遮挡——读作"上面又铺了一张纸"）。
     */
    val Paper = Color(0xFFF7F5F0)
    val PaperShade = Color(0xFFEDE9DF)
    val PaperSheet = Color(0xFFFFFFFF)

    /**
     * 墨：四级浓淡（全站唯一的分层手段）。
     *
     * 每一级都要能**当正文用**，因此都满足 WCAG 2.1 AA（小字 ≥ 4.5:1）：
     * 对纸面实测 Black 15.97:1 / Grey 8.35:1 / Light 5.31:1；最淡的 [Faint] 只用于
     * **禁用态文字**（WCAG 对禁用控件不要求对比度，它的"淡"正是要表达不可用）。
     * 改任何一个色值前先跑一次对比度核算（`docs/07` §5 有量算脚本）。
     */
    val Black = Color(0xFF1B1A17)
    val Grey = Color(0xFF4B4843)
    val Light = Color(0xFF6A655C)
    val Faint = Color(0xFF8B867C)

    /** 墨的浓淡层级（唯一的分层手段）。 */
    object Alpha {
        const val Line = 0.88f
        const val LineSoft = 0.42f
        const val Hair = 0.16f
        const val Wash = 0.07f
        const val WashStrong = 0.14f
        const val Disabled = 0.26f
        /** 模态遮罩：覆盖层的"纸背压暗"程度（全站统一，避免各覆盖层深浅不一）。 */
        const val Mask = 0.16f
        /** 上层纸片的不透明度：浮层纸面半透明时底下的网格隐约可见（读作"薄纸"）。 */
        const val Sheet = 0.94f
        /** 装饰墨点的基础浓度与逐枚递增步长（首页手绘草图）。 */
        const val Decor = 0.20f
        const val DecorStep = 0.06f
        /**
         * 候选数字（空格里的半透明灰数字）。
         *
         * 它是**冗余通道**：同一信息在悬浮面板（只列可填）与数字键盘的重墨强调里都能拿到，
         * 所以刻意不做 AA 对比度——做得太黑就会和"题目给定"的数字抢层级。
         * 0.50 是在"能读"与"退后"之间取的值（早期 0.42 在缩放下几乎看不见）。
         */
        const val Hint = 0.50f
    }

    // 线宽不在此处定义——统一走 DesignTokens.Stroke（dp 令牌，可适配高 DPI）。

    /**
     * 正文 / 标题字体：**打包霞鹜文楷**（LXGW WenKai，OFL 1.1），取不到再回退系统楷书 → 衬线体。
     *
     * 为什么从"只探系统字体"改为"打包分发"：跨平台字形一致（`docs/07` P1-3），
     * 不同设备不再因为缺字体而退回衬线体。授权文件见 `resources/fonts/OFL-LXGWWenKai.txt`。
     */
    val FontText: FontFamily get() = textFontFamily() ?: FontFamily.Serif

    /**
     * 数字字体：**打包 Nunito**（圆润人文无衬线，OFL 1.1），**中文回退霞鹜文楷**。
     *
     * 用于一切"数字读数"：棋盘格内数字 / 笔记 / 候选、数字键盘键位、计时、进度、通关用时。
     * 混进中文时（如暂停时计时位显示「已暂停」）由霞鹜文楷接住，不会出现豆腐块。
     */
    val FontDigits: FontFamily get() = digitFontFamily() ?: FontFamily.SansSerif

    /** 墨字（正文）：默认字距略放宽，模拟手写呼吸感。 */
    fun style(
        size: TextUnit,
        color: Color = Black,
        weight: FontWeight = FontWeight.Normal,
        letterSpacing: TextUnit = 0.6.sp,
        lineHeight: TextUnit = TextUnit.Unspecified,
    ) = TextStyle(
        fontFamily = FontText,
        fontSize = size,
        color = color,
        fontWeight = weight,
        letterSpacing = letterSpacing,
        lineHeight = lineHeight,
    )

    /**
     * 数字专用样式：与正文同一套墨色与字号，但换 [FontDigits]、字距收紧为 0。
     *
     * 数字不是方块字，不需要正文那 0.6sp 的"呼吸字距"——收紧后多位数（如候选、计时）更稳。
     * 用于棋盘格内数字 / 笔记 / 候选，以及数字键盘键位；**句子里的数字仍走 [style]**（读作"文字里的数字"）。
     */
    fun digitStyle(
        size: TextUnit,
        color: Color = Black,
        weight: FontWeight = FontWeight.Normal,
        letterSpacing: TextUnit = 0.sp,
        lineHeight: TextUnit = TextUnit.Unspecified,
    ) = TextStyle(
        fontFamily = FontDigits,
        fontSize = size,
        color = color,
        fontWeight = weight,
        letterSpacing = letterSpacing,
        lineHeight = lineHeight,
    )

    /**
     * **排版阶梯：全站唯一的字号出口**（共六档，含字号 / 字距 / 行高三要素）。
     *
     * 为什么是这六档：
     * - 手写体笔画细、字面小，阶梯必须**拉开**（12 → 40，相邻档比值 1.15–1.4）。
     *   此前散落着 13 档（10/11/12/13/14/15/16/17/18/21/22/28/40），其中 11 与 12、
     *   17 与 18 在屏幕上毫无区别——那不是层级，只是魔法值堆积；
     * - 字距随字号**反向放大**：小字疏排（呼吸感）、大字更疏（手写标题的题感）；
     * - **不用字重做层级**：系统手写体没有真正的 Bold，Compose 合成的粗体会发虚、
     *   破坏"手绘"的工艺感。层级只靠「字号 + 字距 + 墨的浓淡」——与"只有纸与墨"同源；
     * - 行高全部显式给定（此前全站 0 处），多行文本才有稳定节奏。
     */
    object Type {
        /** 角标 / 脚注 / 键位提示。 */
        val Meta = style(12.sp, letterSpacing = 1.6.sp, lineHeight = 18.sp)

        /** 副说明 / 次要信息 / 紧凑按钮文字。 */
        val Caption = style(14.sp, letterSpacing = 0.8.sp, lineHeight = 22.sp)

        /** 正文 / 按钮 / 数字键。 */
        val Body = style(16.sp, letterSpacing = 0.6.sp, lineHeight = 24.sp)

        /** 抽屉 / 面板标题、强调按钮。 */
        val Title = style(20.sp, letterSpacing = 2.0.sp, lineHeight = 30.sp)

        /** 页面主标题、状态大字（如"已暂停"）。 */
        val Headline = style(28.sp, letterSpacing = 4.0.sp, lineHeight = 40.sp)

        /** 首页主标识（全站唯一超大字，极疏排，当作图形而非文字使用）。 */
        val Display = style(40.sp, letterSpacing = 6.0.sp, lineHeight = 56.sp)
    }
}

/** 由平台提供正文字体（桌面加载打包的霞鹜文楷，失败再探系统楷书）；取不到返回 null，[Ink.FontText] 回退衬线体。 */
expect fun textFontFamily(): FontFamily?

/** 由平台提供数字字体（桌面加载打包的 Nunito）；取不到返回 null，[Ink.FontDigits] 回退无衬线体。 */
expect fun digitFontFamily(): FontFamily?

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
