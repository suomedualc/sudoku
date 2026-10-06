package org.example.sudoku.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 尺寸令牌：间距 / 圆角 / 断点 / 线宽。
 *
 * **颜色与字体不在本文件**——它们统一由 [Ink] 提供（纸墨两色 + 手写体）。
 * 这样"尺寸"与"视觉语言"各只有一个出口，改风格时不至于漏改。
 */
object DesignTokens {
    /** 间距：手写纸风格需要更宽的留白，节奏比 Material 更疏。 */
    object Spacing {
        val Xs: Dp = 4.dp
        val Sm: Dp = 8.dp
        val Md: Dp = 16.dp
        val Lg: Dp = 24.dp
        val Xl: Dp = 32.dp
        val Xxl: Dp = 48.dp
    }

    /** 圆角：手绘方框的圆角，宁小勿大（过大就不像手画的了）。 */
    object Radius {
        val Cell: Dp = 6.dp
        val Button: Dp = 12.dp
        val Panel: Dp = 16.dp
    }

    /** 关键尺寸。 */
    object Sizes {
        /** 宽屏断点：宽度达到该值且宽 > 高时，对局页采用「棋盘左 + 控制右」双栏。 */
        val WideBreakpoint: Dp = 720.dp

        /** 对局页双栏时右侧控制栏宽度。 */
        val ControlPanel: Dp = 360.dp

        /** 首页菜单内容最大宽度（居中留白）。 */
        val MenuMaxWidth: Dp = 400.dp

        /** 首页主菜单项高度。 */
        val MenuItemHeight: Dp = 60.dp

        /** 次级按钮（紧凑）高度。 */
        val CompactItemHeight: Dp = 42.dp

        /** 首页底部装饰棋盘的边长。 */
        val SketchSize: Dp = 168.dp

        /** 对局页为底部提示条（Snackbar）预留的高度，避免提示盖住棋盘最后一行。 */
        val SnackbarReserve: Dp = 64.dp

        /** 选择框 / 棋盘外框的内缩。 */
        val BoardInset: Dp = 4.dp

        /**
         * 悬浮数字面板（鼠标 / 触控笔点空格时出现）：键尺寸 / 内边距 / 键间隔。
         * **面板整体尺寸不固定**——只列出该格"当前可填"的数字，候选越少面板越小、键越大越好按
         * （尺寸由 `FloatingPadPolicy.padWidth/padHeight` 按候选个数算出）。
         */
        val PadKeySize: Dp = 48.dp
        val PadPadding: Dp = 10.dp
        val PadGap: Dp = 6.dp

        /** 悬浮面板与格子的默认间距。 */
        val PadAnchorGap: Dp = 8.dp
    }

    /** 线宽（按 dp 换算，适配高 DPI）。 */
    object Stroke {
        val Hair: Dp = 0.9.dp
        val Thin: Dp = 1.2.dp
        val Bold: Dp = 1.8.dp
        val Frame: Dp = 2.2.dp
        val Grid: Dp = 1.0.dp
        val GridBold: Dp = 2.0.dp
        val Selection: Dp = 2.4.dp
    }
}
