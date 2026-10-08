package org.example.sudoku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.sudoku.core.Game
import org.example.sudoku.core.Sudoku
import org.example.sudoku.state.GameAction
import org.example.sudoku.state.GameState
import org.example.sudoku.state.KeyAction
import org.example.sudoku.state.KeyMap
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
import org.example.sudoku.ui.components.KeyHintRow
import org.example.sudoku.ui.components.KeyMapSheet
import org.example.sudoku.ui.components.KeyToken
import org.example.sudoku.ui.components.InkText
import org.example.sudoku.ui.components.InkTitleFrame
import org.example.sudoku.ui.components.InkToggleRow
import org.example.sudoku.ui.components.NumberPad
import org.example.sudoku.ui.components.TopDrawer
import org.example.sudoku.ui.components.rememberTopDrawerController
import org.example.sudoku.ui.i18n.Strings
import org.example.sudoku.ui.i18n.stringsFor
import org.example.sudoku.ui.platform.SystemBackHandler
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/** 抽屉标识：通关结算（模态，必须明确选择）。 */
private const val DRAWER_WIN = "game.win"

/** 抽屉标识：键位设置（可 Esc / 点遮罩关闭）。 */
private const val DRAWER_KEYMAP = "game.keymap"

/** 抽屉标识：游戏设置（窄屏时四项偏好收进抽屉，不占常驻布局空间）。 */
private const val DRAWER_TOGGLES = "game.toggles"

/**
 * 棋盘之外那层可点空白的测试标签。
 *
 * 它只在测试里被点到——UI 测试没法"点一个没有节点的地方"，而"点空白处应该清掉选中"
 * 这条行为必须能自动验收（靠肉眼看高亮有没有消失太不可靠）。
 */
internal const val BoardBackdropTag = "game.boardBackdrop"

/**
 * 对局界面（手写纸 · 油墨）。
 *
 * 布局：**纵向三段式**——顶栏 → 棋盘舞台 → 功能区（数字键一行 + 开关一行）。
 * 棋盘**左右两侧不放任何东西**，题面四周因此是干净的纸；棋盘与功能区同宽并居中，
 * 整页只有一条自上而下的中轴，视线不用左右来回找。
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
    // 全部可见文字从字典取词：语言是快照状态，切换即整页重组（无需重启）
    val strings = stringsFor(state.language)
    val focusRequester = remember { FocusRequester() }
    val drawer = rememberTopDrawerController()

    // 系统返回键（Android，缺陷 D2）：返回键不进抽屉的模态仲裁（TopDrawerKeys 对 Back 一律放行——
    // 吞掉它的话 Activity 的返回分发收不到事件，抽屉开着按返回就毫无反应）。
    // 语义在此统一裁决：抽屉开着且可关 → 先关抽屉；结算抽屉（不可关）与无抽屉 → 回首页
    // （对局已自动存档，与顶栏返回同义）。首页不拦截——系统默认行为（退出应用）。桌面端无系统返回键，actual 为空实现。
    SystemBackHandler {
        if (drawer.isOpen && drawer.dismissible) drawer.close()
        else onAction(GameAction.Navigate(Screen.Menu))
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // 开局 / 续局 / 换了一局：光标落在**第一个空格**（读序），键盘玩家不用先按一次方向键就能开始填。
    // key 用 `state.game`：只在"换了一局"（新局 / 重置 / 撤销重做改了盘面）时重跑，
    // 因此"点棋盘外的空白清掉选中"之后不会被它硬拉回来（那正是玩家想要的状态）。
    LaunchedEffect(state.game) { onAction(GameAction.FocusFirstEmpty) }

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

    // 键位设置：正在等待按键的**动作**（点某一条 → 进入捕获态 → 下一个按键就绑给它）。
    // 放在页面而不是抽屉里：按键是在页面根节点的 onPreviewKeyEvent 收的，抽屉收不到。
    var capturing by remember { mutableStateOf<KeyAction?>(null) }
    /** 捕获失败的原因（例如"数字 1–9 留给填数"）；绑上了或取消时清空。 */
    var captureHint by remember { mutableStateOf<String?>(null) }
    // 抽屉一关就退出捕获态（否则下次打开会残留一个"正在等你按键"的按钮）
    LaunchedEffect(drawer.isOpen(DRAWER_KEYMAP)) {
        if (!drawer.isOpen(DRAWER_KEYMAP)) {
            capturing = null
            captureHint = null
        }
    }

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

    /**
     * 把棋盘恢复成"刚开局"的干净样子：收起悬浮面板 + 取消选格。
     *
     * 选中框、同行列宫高亮、同数字高亮都是从 `selected` 派生出来的，清掉它就全没了。
     * **冲突排线不清**——那是对局数据（真的填重了），不是临时标记，清掉等于骗玩家。
     */
    val clearBoard: () -> Unit = {
        padCell = null
        onAction(GameAction.Deselect)
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

    /**
     * 键位设置抽屉内的键：**捕获态时任何可识别的按键都算"绑定"**。
     *
     * 几条细节：
     * - `Esc` **先**退出捕获态、不关抽屉（关抽屉是"没有捕获"时的行为）——
     *   否则玩家想取消一次绑定，却把整个抽屉关掉了；
     * - **退格 = 清掉现有设置**（该动作复位为默认键位），并且**保持捕获态**——
     *   玩家的动作序列是"清掉 → 直接按新键"，中间不必再点一次按钮；
     * - 绑不了的键（修饰键 / F1 等）**保持捕获态**并继续等，不当成"确认"，
     *   否则按一下 Shift 就等于绑了个按不出来的键。
     */
    val keyMapDrawerKeys: (KeyEvent) -> Boolean = { event ->
        val target = capturing
        if (target == null) {
            false
        } else if (event.key == Key.Escape) {
            capturing = null
            true
        } else if (event.key == Key.Backspace) {
            // "清除现有设置"：复位为该动作的默认键位，然后**继续等新键**。
            // 每个动作永远有一个键可用——清空不是"没有键"，否则方向键就动不了了。
            dispatch(GameAction.BindKey(target, KeyMap.DEFAULT.token(target)))
            true
        } else {
            val token = KeyToken.of(event)
            when {
                // 绑不了的键（修饰键 / F1 等）：继续等，别把它当成"确认"
                token == null -> Unit
                !state.keyMap.accepts(target, token) -> {
                    captureHint = strings.digitsReserved
                    capturing = null
                }
                else -> {
                    dispatch(GameAction.BindKey(target, token))
                    captureHint = null
                    capturing = null
                }
            }
            true // 无论绑没绑上，捕获态下这个键都不再往下走
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
            append(strings.boardDesc.format(strings.difficulty(game.difficulty), done, total))
            if (state.paused) append(strings.boardPaused)
            if (state.noteMode) append(strings.boardNoteMode)
            if (state.hintCandidates) append(strings.boardCandidates)
            state.selected?.let {
                append(strings.boardSelected.format(Sudoku.rowOf(it) + 1, Sudoku.colOf(it) + 1))
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                // 键序：抽屉（模态，打开时吞掉一切）→ 悬浮面板（Esc 先收它）→ 棋盘快捷键
                drawer.handleKey(event) { e ->
                    when {
                        drawer.isOpen(DRAWER_WIN) -> winDrawerKeys(e)
                        drawer.isOpen(DRAWER_KEYMAP) -> keyMapDrawerKeys(e)
                        else -> false
                    }
                } ||
                    handleEscForPad(event) ||
                    handleKeyEvent(event, state, dispatch)
            }
            .focusable(),
    ) {
        val pad = DesignTokens.Spacing.Md
        // 功能区的**窄屏断点**（页宽 < 460dp）：数字键盘切九宫格、四项开关收进设置抽屉——
        // 横排 1–9 至少需要 ~500dp，硬塞进窄页会把键压成不可点的细条（docs/08 §4-D1）
        val narrow = maxWidth < DesignTokens.Sizes.FunctionNarrowMax
        // 功能区的高度（数字键一行或九宫格 + 偏好开关一行或设置按钮）+ 底部键位提示一行：
        // 棋盘必须避开它们，否则会被挤成一条。窄屏九宫格更高（3×52+2×间距）；
        // 键位提示行在窄屏允许折两行（KeyHintRow maxLines = 2）
        val functionsHeight =
            (if (narrow) DesignTokens.Sizes.KeyHeight * 3 + DesignTokens.Spacing.Sm * 2 else DesignTokens.Sizes.KeyHeight) +
                DesignTokens.Spacing.Sm + DesignTokens.Sizes.CompactItemHeight
        // 纵向必须为这些让出位置：顶栏 + 上下留白 + 棋盘上下的两条浮动条槽 +
        // 棋盘与功能区之间的间距 + 功能区 + 键位提示 + 底部提示条
        val reserved =
            DesignTokens.Sizes.TopBarHeight +
                pad * 2 +
                DesignTokens.Sizes.FloatingBarSlot * 2 +
                pad +
                functionsHeight +
                DesignTokens.Sizes.KeyHintHeight * (if (narrow) 2 else 1) +
                DesignTokens.Sizes.SnackbarReserve
        val boardSide = minOf(
            maxWidth - pad * 2,
            (maxHeight - reserved).coerceAtLeast(DesignTokens.Sizes.BoardMinSize),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏：左「返回菜单」· 中状态读数 · 右「提示 / 暂停 / 重置 + 明暗切换」
            GameTopBar(
                game = game,
                state = state,
                done = done,
                total = total,
                narrow = narrow,
                onAction = dispatch,
                onOpenKeyMap = { drawer.open(DRAWER_KEYMAP) },
            )

            // 内容区：**纵向一列**——棋盘 → 数字键 → 偏好开关，整体居中。
            // 棋盘**左右不再有任何元素**（此前右侧还立着 360dp 的控制栏），题面四周彻底空出来。
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // 铺满的"空白层"，就垫在内容**下面**：点它 = 把棋盘恢复干净。
                // 为什么用"下层兄弟"而不是在外层挂手势：命中测试对兄弟是**命中即剪枝**——
                // 点到棋盘 / 按键 / 开关时命中的是上层那些节点，空白层根本不会进事件链；
                // 只有点在空隙上（棋盘四周、两排之间的间距）才会落到它。
                // 此前在外层用 `awaitFirstDown(requireUnconsumed = true)`，虽然行为也对，
                // 但那多出来的父级指针节点会吃掉棋盘的悬停事件（浮动条因此不再跟随鼠标）——
                // 这个坑是实机冒烟 `-Flow bar` 抓到的。
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .testTag(BoardBackdropTag)
                        .pointerInput(Unit) { detectTapGestures { clearBoard() } },
                )
                Column(
                    modifier = Modifier.fillMaxSize().padding(pad),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BoardStage(
                        strings = strings,
                        game = game,
                        state = state,
                        conflicts = conflicts,
                        description = boardDescription,
                        padCell = padCell,
                        onAction = dispatch,
                        onCellClick = onCellClick,
                        expandVertically = false,
                        // 只定**宽度**：高度由内容（两条浮动条槽 + 正方形棋盘）决定，
                        // 用 size() 会把棋盘压扁——这正是"容器不是方的 → 读屏与实际错位"的来源。
                        modifier = Modifier.width(boardSide),
                    )
                    Spacer(Modifier.height(pad))
                    // 功能区与棋盘**同宽**：整页因此是一条自上而下的中轴，视线不用左右找
                    FunctionArea(
                        strings = strings,
                        game = game,
                        state = state,
                        narrow = narrow,
                        onOpenToggles = { drawer.open(DRAWER_TOGGLES) },
                        onAction = dispatch,
                        modifier = Modifier.width(boardSide),
                    )
                    Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                    // 键位提示占**整页宽**（不跟棋盘同宽）：文案长，挤在棋盘那一列会折行，
                    // 一折行就会把预留的高度撑破、反过来挤小棋盘
                    KeyHintRow(strings = strings, keyMap = state.keyMap, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // 通关抽屉：从顶部滑下、盖住整页，玩家必须明确选择「再来一局」或「返回首页」
        WinDrawer(
            visible = drawer.isOpen(DRAWER_WIN),
            strings = strings,
            difficultyLabel = strings.difficulty(game.difficulty),
            elapsed = state.elapsed,
            hintsCount = state.hintsCount,
            restoreFocus = focusRequester,
            onPlayAgain = { dispatch(GameAction.NewGame(game.difficulty)) },
            onBackToMenu = { dispatch(GameAction.Navigate(Screen.Menu)) },
        )

        // 键位设置抽屉：可 Esc / 点遮罩关闭（它不是结算，不需要强制选择）
        TopDrawer(
            visible = drawer.isOpen(DRAWER_KEYMAP),
            onDismiss = { drawer.close() },
            restoreFocus = focusRequester,
            a11yTitle = strings.keymapA11y,
        ) {
            KeyMapSheet(
                strings = strings,
                keyMap = state.keyMap,
                capturing = capturing,
                captureHint = captureHint,
                onStartCapture = { captureHint = null; capturing = it },
                onCancelCapture = { captureHint = null; capturing = null },
                onResetDefaults = {
                    for (action in KeyAction.entries) {
                        dispatch(GameAction.BindKey(action, KeyMap.DEFAULT.token(action)))
                    }
                },
            )
        }

        // 游戏设置抽屉（**窄屏专属**）：四项偏好从常驻开关行收进抽屉——
        // Esc / 点遮罩关闭；开关即时生效并随存档落盘
        TopDrawer(
            visible = drawer.isOpen(DRAWER_TOGGLES),
            onDismiss = { drawer.close() },
            restoreFocus = focusRequester,
            a11yTitle = strings.gameSettings,
        ) {
            InkText(
                text = strings.gameSettings,
                modifier = Modifier.fillMaxWidth(),
                style = Ink.Type.Title,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.Spacing.Md))
            InkToggleRow(
                label = strings.toggleNoteMode,
                checked = state.noteMode,
                onCheckedChange = { dispatch(GameAction.ToggleNoteMode) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(DesignTokens.Spacing.Xs))
            InkToggleRow(
                label = strings.toggleShowNotes,
                checked = state.showNotes,
                onCheckedChange = { dispatch(GameAction.ToggleShowNotes) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(DesignTokens.Spacing.Xs))
            InkToggleRow(
                label = strings.toggleHintCandidates,
                checked = state.hintCandidates,
                onCheckedChange = { dispatch(GameAction.ToggleHintCandidates) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(DesignTokens.Spacing.Xs))
            InkToggleRow(
                label = strings.toggleStrict,
                checked = state.strictMode,
                onCheckedChange = { enabled -> dispatch(GameAction.ToggleStrict(enabled)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 通关抽屉：用 [TopDrawer] 从顶部滑下（自带滑入动画），给出本局成绩并支持直接再来一局。
 * 不可点遮罩关闭（[TopDrawer] 的 `dismissible = false`）——避免"随手一点就丢了结算信息"。
 */
@Composable
private fun WinDrawer(
    visible: Boolean,
    strings: Strings,
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
        a11yTitle = strings.winTitle,
    ) {
        InkTitleFrame(
            title = strings.winTitle,
            subtitle = strings.winTimeLabel + " " + Sudoku.formatDuration(elapsed),
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Md))
        WinRow(strings.winDifficultyLabel, difficultyLabel)
        WinRow(strings.winHintsLabel, if (hintsCount == 0) strings.hintsNone else "×$hintsCount")
        Spacer(Modifier.height(DesignTokens.Spacing.Lg))
        InkButton(strings.playAgain, onPlayAgain, emphasized = true, compact = true)
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        InkButton(strings.backHome, onBackToMenu, compact = true)
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        InkText(
            text = strings.winKeys,
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
    narrow: Boolean,
    onAction: (GameAction) -> Unit,
    /** 打开键位设置抽屉（抽屉状态在页面里，这里只发信号）。 */
    onOpenKeyMap: () -> Unit,
) {
    val strings = stringsFor(state.language)
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
            contentDescription = strings.backToMenu,
            onClick = { onAction(GameAction.Navigate(Screen.Menu)) },
            // 贴左边缘：提示片向右长，否则会被窗口左边缘切掉
            tooltipAlignment = Alignment.TopStart,
        )

        // 中间用 weight 占满，把右侧控制组顶到最右。
        // 窄屏：只保留难度 + 计时两个主读数——次要读数（提示计数 / 进度）在 460dp 下
        // 会把中间区挤到截断（docs/08 §4-D1），读屏文案（boardDescription）里仍可获取。
        Row(
            modifier = Modifier.weight(1f).padding(horizontal = DesignTokens.Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md),
        ) {
            InkText(text = strings.difficulty(game.difficulty), style = Ink.Type.Caption.copy(color = Ink.Grey))
            InkText(
                text = if (state.paused) strings.pausedLabel else Sudoku.formatDuration(state.elapsed),
                // 计时是数字读数：走 Nunito 且字距为 0——秒数跳动时不会因等宽与否而左右晃
                style = Ink.digitStyle(Ink.Type.Body.fontSize),
            )
            if (state.hintsCount > 0 && !narrow) {
                InkText(
                    text = strings.hintsUsed.format(state.hintsCount),
                    style = Ink.Type.Caption.copy(color = Ink.Grey),
                )
            }
            if (!narrow) {
                InkText(
                    text = "$done / $total",
                    style = Ink.digitStyle(Ink.Type.Body.fontSize, color = Ink.Grey),
                )
            }
        }

        // 右侧这一组**都贴右边缘**：提示片一律向左长，才不会被窗口右边缘切掉。
        // 键位设置放在这一组的**最前面**：新增按钮时保持明暗切换仍在最后一位，
        // 顶栏最右侧那枚按钮的位置才不会变（冒烟脚本按固定坐标点它）。
        InkIconButton(
            icon = InkIcon.Keyboard,
            contentDescription = strings.keymapA11y,
            tooltip = strings.keymapTooltip,
            onClick = onOpenKeyMap,
            tooltipAlignment = Alignment.TopEnd,
        )
        InkIconButton(
            icon = InkIcon.Hint,
            contentDescription = strings.hint,
            onClick = { onAction(GameAction.Hint) },
            enabled = !state.settled && !state.paused,
            tooltipAlignment = Alignment.TopEnd,
        )
        InkIconButton(
            icon = if (state.paused) InkIcon.Play else InkIcon.Pause,
            contentDescription = if (state.paused) strings.resume else strings.pause,
            onClick = { onAction(if (state.paused) GameAction.Resume else GameAction.Pause) },
            enabled = !state.settled,
            tooltipAlignment = Alignment.TopEnd,
        )
        InkIconButton(
            icon = InkIcon.Reset,
            contentDescription = strings.resetGame,
            onClick = { onAction(GameAction.Reset) },
            enabled = !state.settled && !state.paused,
            tooltipAlignment = Alignment.TopEnd,
        )
        InkIconButton(
            icon = if (state.darkMode) InkIcon.Sun else InkIcon.Moon,
            contentDescription = if (state.darkMode) strings.toLight else strings.toNight,
            // 悬停提示用短名：读屏描述可以啰嗦（"切回浅色纸面"），浮出来的标签要一眼看完
            tooltip = if (state.darkMode) strings.tooltipPaper else strings.tooltipNight,
            onClick = { onAction(GameAction.ToggleDarkMode) },
            tooltipAlignment = Alignment.TopEnd,
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
    strings: Strings,
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
        BarSlot(visible = barSide == BarSide.Top) { UndoRedoBar(strings, state, onAction) }

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
                    // 只看"指针在哪"，不消费任何事件——棋盘的点击照常
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                // 判据用"**没按下**"而不是"事件类型 == Move"：
                                // 指针**刚进入**容器时 Compose 报的是 Enter、之后才是 Move，
                                // 只认 Move 就会漏掉"一步跨进棋盘"的情形（实机 `SetCursorPos` 正是这种），
                                // 表现为"鼠标明明在棋盘上半部，条却不动"。未按下 = 悬停，对两种类型都成立；
                                // 手指拖动时 pressed = true，因此不会拿拖动的位置去挪条。
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed) continue
                                barSide = FloatingBarPolicy.next(
                                    previous = barSide,
                                    pointerY = change.position.y,
                                    boardTop = 0f,
                                    boardHeight = boardHeight.toFloat(),
                                )
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
                    language = state.language,
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
                    onCellClick = onCellClick,
                )
                if (padCell != null && state.interactive) {
                    FloatingInputPad(
                        cell = padCell,
                        enabled = state.interactive,
                        strings = strings,
                        // 只列这一格当前可填的数字（笔记模式下也用这份候选——想记"不可能的数字"就用常驻键盘）。
                        // 按盘面缓存：计时每秒触发重组，逐帧扫 81 格白算一遍。
                        legalMask = remember(game.current, padCell) { Sudoku.legalMask(game.current, padCell) },
                        onDigit = { onAction(GameAction.Digit(it)) },
                    )
                }
                if (state.paused) PauseVeil(strings, onAction)
            }
        }

        BarSlot(visible = barSide == BarSide.Bottom) { UndoRedoBar(strings, state, onAction) }
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
private fun UndoRedoBar(
    strings: Strings,
    state: GameState,
    onAction: (GameAction) -> Unit,
) {
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
                contentDescription = strings.undo,
                onClick = { onAction(GameAction.Undo) },
                enabled = state.undoStack.isNotEmpty(),
            )
            InkIconButton(
                icon = InkIcon.Redo,
                contentDescription = strings.redo,
                onClick = { onAction(GameAction.Redo) },
                enabled = state.redoStack.isNotEmpty(),
            )
        }
    }
}

/** 暂停遮挡：用一张纸盖住题面——"暂停"意味着不能继续读题（读屏同步遮住，见 [BoardCanvas]）。 */
@Composable
private fun PauseVeil(strings: Strings, onAction: (GameAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.PaperSheet),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InkText(text = strings.pausedLabel, style = Ink.Type.Headline)
        Spacer(Modifier.height(DesignTokens.Spacing.Lg))
        InkButton(
            text = strings.resume,
            onClick = { onAction(GameAction.Resume) },
            modifier = Modifier.width(DesignTokens.Sizes.OverlayButtonWidth),
            emphasized = true,
            compact = true,
        )
    }
}

/**
 * 功能区：**纵向排列**——先数字键一行，再偏好开关一行。
 *
 * 此前数字键盘与开关立在棋盘**右侧**（360dp），现在挪到棋盘**下方**并压成两行：
 * 棋盘左右两侧因此彻底空出来，整页只有一条自上而下的中轴，视线不用左右来回找。
 * 撤走的不是功能——撤销 / 重做在棋盘上下的浮动条，提示 / 暂停 / 重置 / 菜单在顶栏，
 * 四项偏好就在下面这一行里。
 */
@Composable
private fun FunctionArea(
    strings: Strings,
    game: Game,
    state: GameState,
    narrow: Boolean,
    onOpenToggles: () -> Unit,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm)) {
        NumberRow(strings, game, state, onAction, grid = narrow)
        if (narrow) {
            // 窄屏：四项偏好收进「游戏设置」抽屉（TopDrawer，覆盖层唯一形态）——
            // 常驻开关行在窄屏会把勾选框挤出屏幕（docs/08 §4-D1）
            InkButton(
                text = strings.gameSettings,
                onClick = onOpenToggles,
                compact = true,
            )
        } else {
            ToggleRow(strings, state, onAction)
        }
    }
}

/** 第一排：数字输入（宽屏 1–9 横排；窄屏 3×3 九宫格 + 竖置擦除，见 [NumberPad]）。 */
@Composable
private fun NumberRow(
    strings: Strings,
    game: Game,
    state: GameState,
    onAction: (GameAction) -> Unit,
    grid: Boolean,
) {
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
        strings = strings,
        legalMask = legalMask,
        enabled = state.interactive,
        remaining = remaining,
        onDigit = { onAction(GameAction.Digit(it)) },
        onErase = { onAction(GameAction.Digit(0)) },
        grid = grid,
    )
}

/**
 * 第二排：四项偏好**一行**排开。
 *
 * 竖着排要占 4×42 ≈ 170dp，棋盘就得再矮 130dp——而它们是"设一次就不管"的开关，
 * 不值得拿棋盘的尺寸去换。横排后整条功能区只有 102dp。
 * 代价是每项宽度只有棋盘宽的 1/4：标签一律 4 个字以内，`maxLines = 1` 兜住更长的文案。
 */
@Composable
private fun ToggleRow(strings: Strings, state: GameState, onAction: (GameAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm)) {
        InkToggleRow(
            label = strings.toggleNoteMode,
            checked = state.noteMode,
            onCheckedChange = { onAction(GameAction.ToggleNoteMode) },
            modifier = Modifier.weight(1f),
        )
        InkToggleRow(
            label = strings.toggleShowNotes,
            checked = state.showNotes,
            onCheckedChange = { onAction(GameAction.ToggleShowNotes) },
            modifier = Modifier.weight(1f),
        )
        InkToggleRow(
            label = strings.toggleHintCandidates,
            checked = state.hintCandidates,
            onCheckedChange = { onAction(GameAction.ToggleHintCandidates) },
            modifier = Modifier.weight(1f),
        )
        InkToggleRow(
            label = strings.toggleStrict,
            checked = state.strictMode,
            onCheckedChange = { enabled -> onAction(GameAction.ToggleStrict(enabled)) },
            modifier = Modifier.weight(1f),
        )
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
        // 物理键盘填数 → 填完自动跳到下一个空格（策略见 CellCursor.nextAfterFill）。
        // 屏幕上的数字键不跳：鼠标玩家自己点下一格更自然。
        onAction(GameAction.Digit(digit, advance = true))
        return true
    }

    // 自定义键位：令牌匹配（1–9 留给填数，绑不到这里，见 KeyMap.accepts）
    val token = KeyToken.of(event)
    if (token != null) {
        val move = state.keyMap.move(token)
        if (move != null) {
            // 默认逐格（可停在已填格，回去改 / 擦）；Shift = 跳到下一个空格（快速推进）
            onAction(GameAction.Move(move.first, move.second, jumpToBlank = event.isShiftPressed))
            return true
        }
        if (state.keyMap.isErase(token)) {
            onAction(GameAction.Digit(0))
            return true
        }
    }

    return when (event.key) {
        // 兜底（**不受键位设置影响**）：方向键始终可移动、退格 / Del 始终可擦除。
        // 键位设置是为了更好用，不能让人把自己锁在门外——这是三条硬兜底。
        Key.Delete, Key.Backspace -> {
            onAction(GameAction.Digit(0))
            true
        }
        Key.DirectionLeft -> { onAction(GameAction.Move(0, -1, event.isShiftPressed)); true }
        Key.DirectionRight -> { onAction(GameAction.Move(0, 1, event.isShiftPressed)); true }
        Key.DirectionUp -> { onAction(GameAction.Move(-1, 0, event.isShiftPressed)); true }
        Key.DirectionDown -> { onAction(GameAction.Move(1, 0, event.isShiftPressed)); true }
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
