package org.example.sudoku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.sudoku.core.Game
import org.example.sudoku.core.Sudoku
import org.example.sudoku.state.GameAction
import org.example.sudoku.state.GameState
import org.example.sudoku.state.Screen
import org.example.sudoku.ui.components.BarSide
import org.example.sudoku.ui.components.BoardCanvas
import org.example.sudoku.ui.components.FloatingBarPolicy
import org.example.sudoku.ui.components.FloatingInputPad
import org.example.sudoku.ui.components.FloatingPadPolicy
import org.example.sudoku.ui.components.InkButton
import org.example.sudoku.ui.components.InkIcon
import org.example.sudoku.ui.components.InkIconButton
import org.example.sudoku.ui.components.InkPanel
import org.example.sudoku.ui.components.InkText
import org.example.sudoku.ui.components.InkTitleFrame
import org.example.sudoku.ui.components.InkToggleRow
import org.example.sudoku.ui.components.NumberPad
import org.example.sudoku.ui.components.TopDrawer
import org.example.sudoku.ui.components.rememberTopDrawerController
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/** 抽屉标识：通关结算（模态，必须明确选择）。 */
private const val DRAWER_WIN = "game.win"

/**
 * 对局界面（手写纸 · 油墨）。
 *
 * 布局自适应：
 * - **宽屏**（宽 ≥ [DesignTokens.Sizes.WideBreakpoint] 且宽 > 高）：棋盘左 + 控制右，棋盘取可用高宽的正方形；
 * - **窄屏**：纵向排布并整体可滚动，棋盘占满宽度。
 * 两种形态都保证"数字键盘与棋盘同屏可见"，空间不足时宁可滚动也不压缩键盘。
 *
 * 输入方式：
 * - 鼠标 / 触屏点选；**鼠标 / 触控笔点空格**会就地弹出半透明悬浮数字面板（[FloatingInputPad]），
 *   手指不弹（会被手指挡住），触屏走常驻数字键盘；
 * - 桌面键盘由 [handleKeyEvent] 处理（1–9 填数、0/Delete 擦除、方向键选格并跳过给定格、N 笔记、
 *   H 提示、P/空格 暂停、Esc 继续 / 收面板、Ctrl+Z/Y 撤销重做）；
 * - 通关时改为**模态的顶部抽屉**（[WinDrawer]）：`Enter` 再来一局、`Esc` 返回首页，
 *   其余按键由抽屉统一吞掉，不会再落到棋盘上（见 [rememberTopDrawerController]）。
 */
@Composable
fun GameScreen(
    state: GameState,
    conflicts: BooleanArray,
    progress: Pair<Int, Int>,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = state.game ?: return
    val (done, total) = progress
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val drawer = rememberTopDrawerController()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // 通关是"数据驱动"的状态，抽屉开关跟着它走：不可点遮罩关闭，必须明确选择
    // （「再来一局」/「返回首页」）。离开通关态（再来一局、重置、看答案结算）时自动收起。
    LaunchedEffect(state.won) {
        if (state.won) {
            drawer.open(DRAWER_WIN, dismissible = false)
        } else if (drawer.isOpen(DRAWER_WIN)) {
            drawer.close()
        }
    }

    // 悬浮数字面板：记录"面板锚在哪个格子"。属于纯 UI 细节，因此不进 GameState。
    var padCell by remember { mutableStateOf<Int?>(null) }

    // 选格变化（点棋盘 / 方向键换格）→ 面板跟着走；不关，让"键鼠混用"不中断
    LaunchedEffect(state.selected) {
        val open = padCell
        if (open != null && state.selected != open) padCell = state.selected
    }
    // 暂停 / 通关 / 结算 / 回首页 → 面板立刻收掉（棋盘已不可交互）
    LaunchedEffect(state.interactive) {
        if (!state.interactive) padCell = null
    }

    // 统一出口：除"选格 / 换格 / 计时心跳"外的任何动作都收起面板
    // （否则填完数、撤销后，面板还悬在已经变了的盘面上）
    val dispatch: (GameAction) -> Unit = { action ->
        if (!FloatingPadPolicy.keepsOpen(action)) padCell = null
        onAction(action)
    }

    val onCellClick: (Int, Boolean) -> Unit = { cell, precisePointer ->
        padCell = FloatingPadPolicy.nextOnCellClick(padCell, state, cell, precisePointer)
        onAction(GameAction.Select(cell))
    }

    /** Esc 先收悬浮面板：第一次 Esc 只关浮层，再按才走"暂停 / 继续"。 */
    val handleEscForPad: (KeyEvent) -> Boolean = { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && padCell != null) {
            padCell = null
            true
        } else {
            false
        }
    }

    /**
     * 通关抽屉内的键：`Enter` / 空格 再来一局，`Esc` 返回首页（抽屉不可点遮罩关闭）。
     * 抽屉是模态的——棋盘此时已锁定，其余按键由 [rememberTopDrawerController] 统一吞掉。
     */
    val winDrawerKeys: (KeyEvent) -> Boolean = { event ->
        when (event.key) {
            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                dispatch(GameAction.NewGame(game.difficulty))
                true
            }
            Key.Escape -> {
                dispatch(GameAction.Navigate(Screen.Menu))
                true
            }
            else -> false
        }
    }

    // 读屏文案用到的每个字段都必须是 key：漏掉 noteMode 的话，切换笔记模式后描述会停在旧值
    val boardDescription = remember(
        game,
        state.selected,
        state.paused,
        state.noteMode,
        state.hintCandidates,
        done,
        total,
    ) {
        buildString {
            append("数独棋盘，难度 ${game.difficulty.label}，已填 $done / $total")
            if (state.paused) append("，已暂停")
            if (state.noteMode) append("，笔记模式")
            if (state.hintCandidates) append("，候选提示开启")
            state.selected?.let {
                append("，当前选中第 ${Sudoku.rowOf(it) + 1} 行第 ${Sudoku.colOf(it) + 1} 列")
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                // 键序：抽屉（模态，打开时吞掉一切）→ 悬浮面板（Esc 先收它）→ 棋盘快捷键
                drawer.handleKey(event, winDrawerKeys) ||
                    handleEscForPad(event) ||
                    handleKeyEvent(event, state, dispatch)
            }
            .focusable(),
    ) {
        val wide = maxWidth >= DesignTokens.Sizes.WideBreakpoint && maxWidth > maxHeight

        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏：左「返回菜单」· 中状态读数 · 右「提示 / 暂停 / 重置 + 明暗切换」
            GameTopBar(game, state, done, total, dispatch)

            if (wide) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(DesignTokens.Spacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md),
                ) {
                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        // 顶部对齐并预留提示条高度：这样 Snackbar 弹出时不会压住棋盘最后一行
                        val side = minOf(
                            maxWidth,
                            (maxHeight - DesignTokens.Sizes.SnackbarReserve)
                                .coerceAtLeast(DesignTokens.Sizes.BoardMinSize),
                        )
                        BoardStage(
                            game = game,
                            state = state,
                            conflicts = conflicts,
                            description = boardDescription,
                            padCell = padCell,
                            onAction = dispatch,
                            onCellClick = onCellClick,
                            expandVertically = true,
                            modifier = Modifier.align(Alignment.TopCenter).size(side),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .width(DesignTokens.Sizes.ControlPanel)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            // 点控制栏的空白处也收面板（点到开关 / 按钮时由 dispatch 收）
                            .pointerInput(Unit) { detectTapGestures { padCell = null } },
                    ) {
                        PadPanel(game, state, dispatch)
                        Spacer(Modifier.height(DesignTokens.Spacing.Md))
                        OptionsPanel(state, dispatch)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(DesignTokens.Spacing.Md),
                ) {
                    BoardStage(
                        game = game,
                        state = state,
                        conflicts = conflicts,
                        description = boardDescription,
                        padCell = padCell,
                        onAction = dispatch,
                        onCellClick = onCellClick,
                        expandVertically = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(DesignTokens.Spacing.Md))
                    // 只在"面板区"收浮层：这里不含棋盘，不会与棋盘的点击手势抢事件，
                    // 也不会挡住棋盘上的拖动滚动（窄屏需要靠拖动棋盘来滚页面）。
                    Column(modifier = Modifier.pointerInput(Unit) { detectTapGestures { padCell = null } }) {
                        PadPanel(game, state, dispatch)
                        Spacer(Modifier.height(DesignTokens.Spacing.Md))
                        OptionsPanel(state, dispatch)
                    }
                    Spacer(Modifier.height(DesignTokens.Spacing.Sm))
                }
            }
        }

        // 通关抽屉：从顶部滑下、盖住整页，玩家必须明确选择「再来一局」或「返回首页」
        WinDrawer(
            visible = drawer.isOpen(DRAWER_WIN),
            difficultyLabel = game.difficulty.label,
            elapsed = state.elapsed,
            hintsCount = state.hintsCount,
            restoreFocus = focusRequester,
            onPlayAgain = { dispatch(GameAction.NewGame(game.difficulty)) },
            onBackToMenu = { dispatch(GameAction.Navigate(Screen.Menu)) },
        )
    }
}

/**
 * 通关抽屉：用 [TopDrawer] 从顶部滑下（自带滑入动画），给出本局成绩并支持直接再来一局。
 * 不可点遮罩关闭（[TopDrawer] 的 `dismissible = false`）——避免"随手一点就丢了结算信息"。
 */
@Composable
private fun WinDrawer(
    visible: Boolean,
    difficultyLabel: String,
    elapsed: Int,
    hintsCount: Int,
    restoreFocus: FocusRequester,
    onPlayAgain: () -> Unit,
    onBackToMenu: () -> Unit,
) {
    TopDrawer(
        visible = visible,
        onDismiss = {},
        dismissible = false,
        restoreFocus = restoreFocus,
        a11yTitle = "通关结算",
    ) {
        InkTitleFrame(title = "通关", subtitle = "本局用时 ${Sudoku.formatDuration(elapsed)}")
        Spacer(Modifier.height(DesignTokens.Spacing.Md))
        WinRow("难度", difficultyLabel)
        WinRow("提示", if (hintsCount == 0) "未使用" else "×$hintsCount")
        Spacer(Modifier.height(DesignTokens.Spacing.Lg))
        InkButton("再来一局", onPlayAgain, emphasized = true, compact = true)
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        InkButton("返回首页", onBackToMenu, compact = true)
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        InkText(
            text = "Enter 再来一局 · Esc 返回首页",
            modifier = Modifier.fillMaxWidth(),
            style = Ink.Type.Meta.copy(color = Ink.Light),
            textAlign = TextAlign.Center,
        )
    }
}

/** 通关墨框里的一行"标签 —— 值"。 */
@Composable
private fun WinRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkText(text = label, style = Ink.Type.Caption.copy(color = Ink.Grey))
        InkText(text = value, style = Ink.Type.Body)
    }
    Spacer(Modifier.height(DesignTokens.Spacing.Xs))
}

/**
 * 顶栏：**全局操作只有一个出口**。
 *
 * 左「返回菜单」· 中「状态读数」· 右「提示 / 暂停 / 重置 + 明暗切换」。
 * 把控制集中到棋盘上方的一横条，是为了让棋盘四周干净下来——核心区只留给题面。
 * 「撤销 / 重做」是**高频且跟随操作**的一类，另行做成棋盘的浮动条（见 [BoardStage]）。
 *
 * 状态读数从原来的"右上角一列"改到顶栏中间：它不属于操作，属于"看一眼"，横排更省高度。
 */
@Composable
private fun GameTopBar(
    game: Game,
    state: GameState,
    done: Int,
    total: Int,
    onAction: (GameAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DesignTokens.Sizes.TopBarHeight)
            .padding(horizontal = DesignTokens.Spacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm),
    ) {
        InkIconButton(
            icon = InkIcon.Back,
            contentDescription = "返回菜单",
            onClick = { onAction(GameAction.Navigate(Screen.Menu)) },
        )

        // 中间用 weight 占满，把右侧控制组顶到最右
        Row(
            modifier = Modifier.weight(1f).padding(horizontal = DesignTokens.Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md),
        ) {
            InkText(text = game.difficulty.label, style = Ink.Type.Caption.copy(color = Ink.Grey))
            InkText(
                text = if (state.paused) "已暂停" else Sudoku.formatDuration(state.elapsed),
                // 计时是数字读数：走 Nunito 且字距为 0——秒数跳动时不会因等宽与否而左右晃
                style = Ink.digitStyle(Ink.Type.Body.fontSize),
            )
            if (state.hintsCount > 0) {
                InkText(text = "提示 ×${state.hintsCount}", style = Ink.Type.Caption.copy(color = Ink.Grey))
            }
            InkText(
                text = "$done / $total",
                style = Ink.digitStyle(Ink.Type.Body.fontSize, color = Ink.Grey),
            )
        }

        InkIconButton(
            icon = InkIcon.Hint,
            contentDescription = "提示",
            onClick = { onAction(GameAction.Hint) },
            enabled = !state.settled && !state.paused,
        )
        InkIconButton(
            icon = if (state.paused) InkIcon.Play else InkIcon.Pause,
            contentDescription = if (state.paused) "继续" else "暂停",
            onClick = { onAction(if (state.paused) GameAction.Resume else GameAction.Pause) },
            enabled = !state.settled,
        )
        InkIconButton(
            icon = InkIcon.Reset,
            contentDescription = "重置本局",
            onClick = { onAction(GameAction.Reset) },
            enabled = !state.settled && !state.paused,
        )
        InkIconButton(
            icon = if (state.darkMode) InkIcon.Sun else InkIcon.Moon,
            contentDescription = if (state.darkMode) "切回浅色纸面" else "切换到夜墨模式",
            onClick = { onAction(GameAction.ToggleDarkMode) },
        )
    }
}

/**
 * 棋盘舞台：**浮动操作条 + 棋盘 + 悬浮数字面板 + 暂停遮挡**。
 *
 * 三点说明：
 * 1. 上下各留一条 [DesignTokens.Sizes.FloatingBarSlot] 的槽专供浮动条——条会随鼠标换边，
 *    若压在棋盘上就会挡住正在看的那一行；
 * 2. 悬浮面板与棋盘在同一个 Box 里，因此**共用同一套 9×9 像素坐标**，定位不用额外换算
 *    （窄屏滚动时也会跟着棋盘一起移动）；
 * 3. 换边判定在本组件内完成：拿鼠标相对**本容器**的纵坐标算比例（[FloatingBarPolicy]），
 *    这样宽屏 / 窄屏都不需要外界传入任何坐标。
 */
@Composable
private fun BoardStage(
    game: Game,
    state: GameState,
    conflicts: BooleanArray,
    description: String,
    padCell: Int?,
    onAction: (GameAction) -> Unit,
    onCellClick: (Int, Boolean) -> Unit,
    expandVertically: Boolean,
    modifier: Modifier = Modifier,
) {
    var barSide by remember { mutableStateOf(FloatingBarPolicy.Default) }
    // 棋盘容器高度单独记账：**不要在 pointerInput 协程里直接读 `size.height`**——
    // 协程在首次布局之前就启动，那时读到的是 0；而它只在 key 变化时重启，
    // 于是会一直拿着 0（策略里 `boardHeight <= 0` = "量不到，保持原样"），表现为"条永远不动"。
    // 这个 bug 单测抓不到，只有在真机上把鼠标移进棋盘才会暴露。
    var boardHeight by remember { mutableIntStateOf(0) }

    Column(modifier = modifier) {
        BarSlot(visible = barSide == BarSide.Top) { UndoRedoBar(state, onAction) }

        // 外层 Box 吃掉剩余空间，内层**强制正方形**——棋盘在任何窗口比例下都必须是方的。
        // 理由不只是好看：`BoardCanvas` 按 `min(宽, 高)` 画网格，而语义层是按"父容器的 1/9"铺的
        // （`BoardCellSemantics` 用 `matchParentSize` + `fillMax*`），容器一旦不是方的，
        // 读屏报出的格子就会与眼睛看到的格子错位。
        Box(
            modifier = if (expandVertically) {
                Modifier.fillMaxWidth().weight(1f)
            } else {
                Modifier.fillMaxWidth().aspectRatio(1f)
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .onSizeChanged { boardHeight = it.height }
                    // 只观察 Move（悬停移动），不消费任何事件——棋盘的点击照常
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type != PointerEventType.Move) continue
                                val y = event.changes.firstOrNull()?.position?.y ?: continue
                                barSide = FloatingBarPolicy.next(barSide, y, 0f, boardHeight.toFloat())
                            }
                        }
                    },
            ) {
                BoardCanvas(
                    game = game,
                    selected = state.selected,
                    notes = state.notes,
                    conflicts = conflicts,
                    noteMode = state.noteMode,
                    showNotes = state.showNotes,
                    hintCandidates = state.hintCandidates,
                    paused = state.paused,
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
                    onCellClick = onCellClick,
                )
                if (padCell != null && state.interactive) {
                    FloatingInputPad(
                        cell = padCell,
                        enabled = state.interactive,
                        // 只列这一格当前可填的数字（笔记模式下也用这份候选——想记"不可能的数字"就用常驻键盘）。
                        // 按盘面缓存：计时每秒触发重组，逐帧扫 81 格白算一遍。
                        legalMask = remember(game.current, padCell) { Sudoku.legalMask(game.current, padCell) },
                        onDigit = { onAction(GameAction.Digit(it)) },
                    )
                }
                if (state.paused) PauseVeil(onAction)
            }
        }

        BarSlot(visible = barSide == BarSide.Bottom) { UndoRedoBar(state, onAction) }
    }
}

/** 棋盘上 / 下为浮动条预留的槽：条只在这一条带子里出现，绝不压住题面。 */
@Composable
private fun BarSlot(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(DesignTokens.Sizes.FloatingBarSlot),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) content()
    }
}

/**
 * 撤销 / 重做：贴在棋盘上或下的浮动条，位置由 [FloatingBarPolicy] 跟着鼠标决定。
 *
 * 两个按钮的可用性直接跟着撤销 / 重做栈走——栈空即禁用，不用额外状态。
 */
@Composable
private fun UndoRedoBar(state: GameState, onAction: (GameAction) -> Unit) {
    InkPanel(
        // 按内容收紧宽度（fillWidth = false）：两个按钮撑满整宽会变成横贯棋盘的空白带，
        // 既压不住视觉重心、又抢棋盘的注意力。外层 BarSlot 会把它居中。
        padding = PaddingValues(horizontal = DesignTokens.Spacing.Sm, vertical = DesignTokens.Spacing.Xs),
        seed = 77,
        fillWidth = false,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm)) {
            InkIconButton(
                icon = InkIcon.Undo,
                contentDescription = "撤销",
                onClick = { onAction(GameAction.Undo) },
                enabled = state.undoStack.isNotEmpty(),
            )
            InkIconButton(
                icon = InkIcon.Redo,
                contentDescription = "重做",
                onClick = { onAction(GameAction.Redo) },
                enabled = state.redoStack.isNotEmpty(),
            )
        }
    }
}

/** 暂停遮挡：用一张纸盖住题面——"暂停"意味着不能继续读题（读屏同步遮住，见 [BoardCanvas]）。 */
@Composable
private fun PauseVeil(onAction: (GameAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.PaperSheet),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InkText(text = "已暂停", style = Ink.Type.Headline)
        Spacer(Modifier.height(DesignTokens.Spacing.Lg))
        InkButton(
            text = "继续",
            onClick = { onAction(GameAction.Resume) },
            modifier = Modifier.width(DesignTokens.Sizes.OverlayButtonWidth),
            emphasized = true,
            compact = true,
        )
    }
}

/** 数字键盘面板：键盘 + 当前模式提示（填数 / 笔记）。 */
@Composable
private fun PadPanel(game: Game, state: GameState, onAction: (GameAction) -> Unit) {
    InkPanel(
        padding = PaddingValues(DesignTokens.Spacing.Md),
        seed = 55,
    ) {
        InkText(
            text = if (state.noteMode) "笔记模式：点数字记候选" else "点数字填入选中格",
            modifier = Modifier.fillMaxWidth(),
            style = Ink.Type.Meta.copy(color = Ink.Light),
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        // 笔记模式下不限制（要能记"不可能的数字"）；其余按盘面 + 选中格缓存——
        // 计时每秒触发一次重组，不缓存就是每秒白扫一遍 81 格。
        // 注意把判空放进 remember 的 key 里，而不是在分支里各调一次 remember。
        val legalMask = remember(game.current, state.selected, state.noteMode) {
            if (state.noteMode) null else state.selected?.let { Sudoku.legalMask(game.current, it) }
        }
        // 每个数字还剩几个：只在盘面变化时重算（心跳不触发重组计算）
        val remaining = remember(game) {
            IntArray(9) { digit -> 9 - game.current.count { it == digit + 1 } }
        }
        NumberPad(
            legalMask = legalMask,
            enabled = state.interactive,
            remaining = remaining,
            onDigit = { onAction(GameAction.Digit(it)) },
            onErase = { onAction(GameAction.Digit(0)) },
        )
    }
}

/** 玩法开关 + 对局操作。 */
@Composable
private fun OptionsPanel(state: GameState, onAction: (GameAction) -> Unit) {
    InkPanel(
        padding = PaddingValues(DesignTokens.Spacing.Md),
        seed = 41,
    ) {
        InkToggleRow(
            label = "笔记模式",
            checked = state.noteMode,
            onCheckedChange = { onAction(GameAction.ToggleNoteMode) },
        )
        InkToggleRow(
            label = "显示笔记",
            checked = state.showNotes,
            onCheckedChange = { onAction(GameAction.ToggleShowNotes) },
        )
        InkToggleRow(
            label = "候选提示",
            checked = state.hintCandidates,
            onCheckedChange = { onAction(GameAction.ToggleHintCandidates) },
        )
        InkText(
            text = if (state.hintCandidates) {
                "空格里的半透明灰数字 = 规则允许的候选"
            } else {
                "开：在空格里显示可填数字（半透明灰）"
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = DesignTokens.Spacing.Xs),
            style = Ink.Type.Meta.copy(color = Ink.Light),
        )
        InkToggleRow(
            label = "严格模式",
            checked = state.strictMode,
            onCheckedChange = { enabled -> onAction(GameAction.ToggleStrict(enabled)) },
        )
        // 这里曾经挤着 6 个按钮（撤销 / 重做 / 提示 / 暂停 / 重置 / 菜单）。
        // 现在：撤销 · 重做 → 棋盘上下的浮动条；提示 · 暂停 · 重置 → 顶栏；返回菜单 → 顶栏左上角图标。
        // 面板只留"偏好开关"，棋盘四周因此干净下来。
    }
}

/**
 * 桌面键盘映射（仅 KeyDown 生效；暂停 / 结算时只保留撤销重做与继续）。
 *
 * **通关期不在本函数处理**：那时是模态的通关抽屉在管事（`Enter` 再来一局 / `Esc` 返回首页，
 * 其余键一律吞掉），所以这里既没有 `won` 分支，也不会出现"空格被当成继续"的串扰。
 */
private fun handleKeyEvent(
    event: KeyEvent,
    state: GameState,
    onAction: (GameAction) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    if (event.isCtrlPressed && event.key == Key.Z) {
        onAction(if (event.isShiftPressed) GameAction.Redo else GameAction.Undo)
        return true
    }
    if (event.isCtrlPressed && event.key == Key.Y) {
        onAction(GameAction.Redo)
        return true
    }
    if (!state.interactive) {
        if (event.key == Key.Escape || event.key == Key.Spacebar) {
            onAction(GameAction.Resume)
            return true
        }
        return false
    }

    val digit = digitOf(event.key)
    if (digit > 0) {
        onAction(GameAction.Digit(digit))
        return true
    }

    return when (event.key) {
        Key.Zero, Key.NumPad0, Key.Delete, Key.Backspace -> {
            onAction(GameAction.Digit(0))
            true
        }
        Key.DirectionLeft -> { onAction(GameAction.Move(0, -1)); true }
        Key.DirectionRight -> { onAction(GameAction.Move(0, 1)); true }
        Key.DirectionUp -> { onAction(GameAction.Move(-1, 0)); true }
        Key.DirectionDown -> { onAction(GameAction.Move(1, 0)); true }
        Key.N -> { onAction(GameAction.ToggleNoteMode); true }
        Key.H -> { onAction(GameAction.Hint); true }
        Key.P, Key.Spacebar -> { onAction(GameAction.Pause); true }
        else -> false
    }
}

private val NumberKeys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
private val NumpadKeys = listOf(
    Key.NumPad1, Key.NumPad2, Key.NumPad3,
    Key.NumPad4, Key.NumPad5, Key.NumPad6,
    Key.NumPad7, Key.NumPad8, Key.NumPad9,
)

/** 主键盘与小键盘的数字键 → 1..9，非数字键返回 0。 */
private fun digitOf(key: Key): Int {
    val mainIndex = NumberKeys.indexOf(key)
    if (mainIndex >= 0) return mainIndex + 1
    val numpadIndex = NumpadKeys.indexOf(key)
    return if (numpadIndex >= 0) numpadIndex + 1 else 0
}
