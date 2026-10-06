package org.example.sudoku.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.motionDurationMs
import org.example.sudoku.ui.theme.Ink
import org.example.sudoku.ui.theme.inkLine
import org.example.sudoku.ui.theme.inkRoundRect

/**
 * 顶部抽屉的按键仲裁（**纯逻辑**，不依赖 KeyEvent，便于单测）。
 *
 * 为什么需要仲裁：Compose 的 `onPreviewKeyEvent` 是**父节点先于子节点**触发的，
 * 页面根节点上的键盘处理一定会先于抽屉里的处理跑到。如果不管，抽屉打开时
 * 方向键会继续移动底下的菜单、数字键会继续落子——这正是"覆盖层 + 快捷键"的经典冲突。
 *
 * 约定：页面根节点把每个键**先交给** [TopDrawerController.handleKey]，由本策略统一裁决：
 * 1. 没有抽屉 → 控制器直接返回 false，页面照常处理；
 * 2. 抽屉打开 → **模态**：抽屉内已处理的键（↑↓ / Enter / Esc 回首页等）算已消费；
 * 3. 抽屉打开且按了 `Esc` 且抽屉可关 → 关闭抽屉；
 * 4. 其余键（含按键抬起）一律消费，不再落到下面的页面。
 */
object TopDrawerKeys {
    /** [consumed] = 页面是否应停止处理该键；[dismiss] = 是否顺带关闭抽屉。 */
    data class Decision(val consumed: Boolean, val dismiss: Boolean)

    fun decide(
        isKeyDown: Boolean,
        isEscape: Boolean,
        dismissible: Boolean,
        drawerHandled: Boolean,
    ): Decision = when {
        !isKeyDown -> Decision(consumed = true, dismiss = false)            // 按键抬起：模态吞掉
        drawerHandled -> Decision(consumed = true, dismiss = false)         // 抽屉自己的快捷键
        isEscape && dismissible -> Decision(consumed = true, dismiss = true) // Esc 关抽屉
        else -> Decision(consumed = true, dismiss = false)                  // 模态：其余键一律吞掉
    }
}

/**
 * 抽屉控制器：一个页面一个（[rememberTopDrawerController]），同时充当
 * **「当前哪个抽屉开着」的唯一真相**与**键盘仲裁入口**。
 *
 * 用法（页面根节点）：
 * ```
 * val drawer = rememberTopDrawerController()
 * ...
 * .onPreviewKeyEvent { event ->
 *     drawer.handleKey(event) { drawerKeys(it) } || pageKeys(event)
 * }
 * ...
 * TopDrawer(visible = drawer.isOpen(DRAWER_X), onDismiss = { drawer.close() }, ...) { ... }
 * ```
 */
@Stable
class TopDrawerController {
    /** 当前打开的抽屉标识；`null` = 全关。 */
    var activeId: String? by mutableStateOf(null)
        private set

    /** 当前抽屉是否允许 `Esc` / 点遮罩关闭。 */
    var dismissible: Boolean by mutableStateOf(true)
        private set

    val isOpen: Boolean get() = activeId != null

    fun open(id: String, dismissible: Boolean = true) {
        activeId = id
        this.dismissible = dismissible
    }

    fun close() {
        activeId = null
    }

    fun isOpen(id: String): Boolean = activeId == id

    /**
     * 页面根节点在 `onPreviewKeyEvent` 里调用。
     *
     * @param drawerHandler 抽屉内自己的快捷键（方向键移动、Enter 确认……），返回 true = 已处理。
     * @return true = 已消费，页面不要再处理；false = 没有抽屉，页面照常处理。
     */
    fun handleKey(event: KeyEvent, drawerHandler: (KeyEvent) -> Boolean): Boolean {
        if (activeId == null) return false
        val isKeyDown = event.type == KeyEventType.KeyDown
        return handleKeyFlags(
            isKeyDown = isKeyDown,
            isEscape = event.key == Key.Escape,
            drawerHandled = isKeyDown && drawerHandler(event),
        )
    }

    /**
     * 裁决的**纯入口**：只依赖三个布尔量。
     *
     * 单独留这个入口是为了可测：[KeyEvent] 在跨平台测试源集里构造不出来，
     * 而"抽屉打开时哪些键被吞、Esc 该不该关"恰恰是最容易踩坑的地方。
     * 返回 true = 已消费（页面不要再处理）；抽屉可关且按了 Esc 时顺带关闭自己。
     */
    internal fun handleKeyFlags(isKeyDown: Boolean, isEscape: Boolean, drawerHandled: Boolean): Boolean {
        if (activeId == null) return false
        val decision = TopDrawerKeys.decide(
            isKeyDown = isKeyDown,
            isEscape = isEscape,
            dismissible = dismissible,
            drawerHandled = drawerHandled,
        )
        if (decision.dismiss) close()
        return decision.consumed
    }
}

/** 记住一个抽屉控制器（配置 / 状态变化不重建，避免抽屉被意外关掉）。 */
@Composable
fun rememberTopDrawerController(): TopDrawerController = remember { TopDrawerController() }

/**
 * 顶部抽屉（TopDrawer）：**全屏遮罩 + 顶部滑出的纸片**，纯 Compose 实现（不用 Dialog / Popup）。
 *
 * - 全屏：遮罩铺满整个窗口，模态拦截所有指针事件，避免穿透到底下的页面；
 * - 宽度适配：纸片占满窗口宽度，但内容限宽 [DesignTokens.Sizes.DrawerMaxWidth] 并居中，
 *   宽窗口不拉成一条"横带"，窄窗口（移动端）自然铺满；
 * - 动效：纸片从屏幕外**滑入**（240ms 落 / 180ms 收），遮罩同步由淡到浓；
 *   位移用 `graphicsLayer` 平移，不参与布局，滑入过程零重组；
 * - 纸墨语言：白纸面 + 手绘下缘描边 + 顶部抓手段（一根短墨线），没有阴影与圆角卡片感；
 * - 焦点：关闭时把焦点交回页面（[restoreFocus]）——抽屉里的控件拿到过焦点，
 *   不移交的话页面就"按键失灵"了。
 *
 * 键盘：配合 [TopDrawerController]，页面根节点先做仲裁（见 [TopDrawerKeys]）。
 *
 * @param visible 是否展开；收起动画由本组件负责（调用方不必自己延迟卸载）。
 * @param onDismiss 点遮罩（[dismissible] 为 true 时）或 `Esc` 时应执行的动作。
 * @param dismissible false = 必须明确选择（例如通关结算）。
 * @param restoreFocus 页面根节点的 FocusRequester，关闭后自动接管焦点。
 * @param a11yTitle 无障碍面板标题（读屏用），可空。
 */
@Composable
fun TopDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    restoreFocus: FocusRequester? = null,
    a11yTitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        // 减少动效时时长为 0：抽屉瞬时出现，不做"半速滑动"
        animationSpec = tween(
            durationMillis = motionDurationMs(
                if (visible) DesignTokens.Motion.EnterMs else DesignTokens.Motion.ExitMs,
            ),
        ),
        label = "topDrawer",
    )
    var sheetHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(visible) {
        if (!visible) restoreFocus?.requestFocus()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 遮罩：只有展开时才吃点击；收起状态整体透明且不拦截指针，页面照常可点
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink.Black.copy(alpha = Ink.Alpha.Mask * progress))
                .then(
                    if (visible) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { if (dismissible) onDismiss() },
                        )
                    } else {
                        Modifier
                    },
                ),
        )

        DrawerSheet(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { sheetHeight = it.height }
                .graphicsLayer {
                    translationY = -sheetHeight * (1f - progress)
                    alpha = progress
                }
                .then(
                    if (a11yTitle != null) Modifier.semantics { paneTitle = a11yTitle } else Modifier,
                ),
            content = content,
        )
    }
}

/** 纸片本体：白纸面 + 手绘下缘描边 + 顶部抓手段。 */
@Composable
private fun DrawerSheet(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(max = DesignTokens.Sizes.DrawerMaxWidth)
            .fillMaxWidth()
            .drawBehind {
                val radius = DesignTokens.Radius.Panel.toPx()
                // 上缘的两个圆角推出屏幕外，只留"下缘圆角"的纸边（抽屉贴住窗口顶边）
                val rect = Rect(-2f, -radius - 2f, size.width + 2f, size.height - 2f)
                drawRoundRect(
                    color = Ink.PaperSheet,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    cornerRadius = CornerRadius(radius),
                )
                // 错位淡墨描边：用"线"表达纸片浮在页面之上（墨线语言里没有阴影）
                inkRoundRect(
                    rect = Rect(rect.left - 2f, rect.top - 2f, rect.right + 2f, rect.bottom + 2f),
                    radiusPx = radius + 2f,
                    widthPx = DesignTokens.Stroke.Hair.toPx(),
                    color = Ink.Black,
                    seed = 7,
                    alpha = Ink.Alpha.Hair,
                )
                inkRoundRect(
                    rect = Rect(rect.left, rect.top, rect.right, rect.bottom),
                    radiusPx = radius,
                    widthPx = DesignTokens.Stroke.Thin.toPx(),
                    color = Ink.Black,
                    seed = 3,
                    alpha = Ink.Alpha.LineSoft,
                )
                // 抓手段：一根短墨线，暗示"这是可以往下拉的一张纸"
                inkLine(
                    from = Offset(size.width / 2f - DesignTokens.Sizes.DrawerGripWidth.toPx() / 2f, DesignTokens.Sizes.DrawerGripOffset.toPx()),
                    to = Offset(size.width / 2f + DesignTokens.Sizes.DrawerGripWidth.toPx() / 2f, DesignTokens.Sizes.DrawerGripOffset.toPx()),
                    widthPx = DesignTokens.Stroke.Bold.toPx(),
                    color = Ink.Black,
                    seed = 11,
                    alpha = Ink.Alpha.LineSoft,
                )
            }
            .padding(
                PaddingValues(
                    start = DesignTokens.Spacing.Lg,
                    end = DesignTokens.Spacing.Lg,
                    top = DesignTokens.Spacing.Lg,
                    bottom = DesignTokens.Spacing.Lg,
                ),
            ),
        content = content,
    )
}
