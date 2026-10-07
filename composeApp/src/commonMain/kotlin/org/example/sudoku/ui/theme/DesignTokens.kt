package org.example.sudoku.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
        /** 勾选方框（比格子更小的方框，圆角必须更紧，否则像按钮）。 */
        val Mark: Dp = 4.dp
        val Cell: Dp = 6.dp
        val Button: Dp = 12.dp
        val Panel: Dp = 16.dp
    }

    /** 关键尺寸。 */
    object Sizes {
        /**
         * 首次启动的窗口尺寸（也是验收主档）。
         *
         * 1180×900 是"纵列布局"（顶栏 + 棋盘 + 一行功能区）在一屏内舒服放下的尺寸：
         * 棋盘能拿到约 500dp，数字键与开关各占一行仍有富余。
         */
        val WindowDefault: DpSize = DpSize(1180.dp, 900.dp)

        /** 首页菜单内容最大宽度（居中留白）。 */
        val MenuMaxWidth: Dp = 400.dp

        /** 首页主菜单项高度。 */
        val MenuItemHeight: Dp = 60.dp

        /** 次级按钮（紧凑）高度。 */
        val CompactItemHeight: Dp = 42.dp

        /** 首页底部装饰棋盘的边长。 */
        val SketchSize: Dp = 168.dp

        /** 顶部抽屉（TopDrawer）内容的最大宽度：宽窗口居中留白，窄窗口占满。 */
        val DrawerMaxWidth: Dp = 560.dp

        /** 顶部抽屉的下缘圆角外的"抓手段"长度。 */
        val DrawerGripWidth: Dp = 44.dp

        /** 对局页为底部提示条（Snackbar）预留的高度，避免提示盖住棋盘最后一行。 */
        val SnackbarReserve: Dp = 64.dp

        /** 顶栏图标按钮的边长（返回菜单 / 暂停 / 重置 / 明暗切换）。 */
        val IconButton: Dp = 36.dp

        /**
         * 横向数字键盘里「擦除」键的宽度。
         *
         * 1–9 十个键**等分剩余宽度**，`擦除` 单独占固定宽：它是两个汉字，等分会被压得比数字键还窄，
         * 读起来就不像"擦除"了；而给它固定 72dp 后，九个数字键仍能拿到足够宽度。
         */
        val EraseKeyWidth: Dp = 72.dp

        /** 顶栏高度：图标按钮 + 上下呼吸。 */
        val TopBarHeight: Dp = 56.dp

        /**
         * 棋盘上/下为"浮动操作条"（撤销 / 重做）预留的槽高。
         *
         * 为什么留槽而不是让条压在棋盘上：条会随鼠标在棋盘上下之间移动，
         * 压着棋子就会挡住正在看的那一行。**上下各留一个槽**，条在两者之间搬。
         */
        val FloatingBarSlot: Dp = 48.dp

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

        /** 数字键高度（常驻键盘的方块键）。 */
        val KeyHeight: Dp = 52.dp

        /** 勾选方框的边长（开关行右侧的手绘勾框）。 */
        val MarkBox: Dp = 24.dp

        /** 棋盘最小边长（窗口再窄也不让格子挤到看不清数字）。 */
        val BoardMinSize: Dp = 160.dp

        /** 覆盖层里的定宽按钮（如暂停页的"继续"）。 */
        val OverlayButtonWidth: Dp = 160.dp

        /** 顶部抽屉抓手段距下缘的距离。 */
        val DrawerGripOffset: Dp = 12.dp
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
        /** 冲突格排线的线宽（比网格线略细，密排时才不会糊成一块）。 */
        val Hatch: Dp = 1.1.dp
    }

    /**
     * 动效时长（毫秒）：全站只允许这四档。
     *
     * 手绘风格不适合"弹跳 / 过冲"这类机械感曲线，因此统一用 `tween` 的默认
     * FastOutSlowIn 缓动；时长分四档即可：
     * - [Press] 按压 / 底纹等**即时反馈**：必须快，慢了就像卡顿；
     * - [Enter] 覆盖层进入：略慢，让"纸落下来"看得清；
     * - [Exit] 覆盖层退出：**比进入快**——退场不该拖沓（iOS/ Material 通用惯例）；
     * - [Reveal] 「落笔成局」：开局时棋盘由淡到浓显影（**≤ 200ms**，评审里的验收线；
     *   比 [Enter] 短得多——开局不该让人等）。
     */
    object Motion {
        const val PressMs: Int = 90
        const val EnterMs: Int = 240
        const val ExitMs: Int = 180
        const val RevealMs: Int = 160
    }
}
