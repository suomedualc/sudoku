package org.example.sudoku.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.sudoku.core.Game
import org.example.sudoku.core.Sudoku
import org.example.sudoku.state.GameAction
import org.example.sudoku.state.GameState
import org.example.sudoku.state.KeyAction
import org.example.sudoku.state.KeyMap
import org.example.sudoku.state.Screen
import org.example.sudoku.ui.components.FloatingPadPolicy
import org.example.sudoku.ui.components.InkButton
import org.example.sudoku.ui.components.InkText
import org.example.sudoku.ui.components.InkToggleRow
import org.example.sudoku.ui.components.KeyHintRow
import org.example.sudoku.ui.components.KeyMapSheet
import org.example.sudoku.ui.components.KeyToken
import org.example.sudoku.ui.components.TopDrawer
import org.example.sudoku.ui.components.rememberTopDrawerController
import org.example.sudoku.ui.i18n.Strings
import org.example.sudoku.ui.i18n.stringsFor
import org.example.sudoku.ui.platform.SystemBackHandler
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/*
 * 对局页的**编排层**：只看得到"顶栏 → 棋盘舞台 → 功能区"三段与三个抽屉，
 * 具体怎么画都在同目录的兄弟文件里。拆分边界（2026-10-24）：
 *
 * | 文件 | 职责 |
 * |---|---|
 * | GameScreen.kt（本文件） | 布局预算（eserved / oardSide / showNotePad）、抽屉编排、键盘事件入口、读屏文案 |
 * | GameLayout.kt | 竖屏 / 横屏判定纯函数（有单测守着） |
 * | GameTopBar.kt | 顶栏：返回、难度与读数、提示 / 暂停 / 重置 / 明暗 |
 * | GameBoardStage.kt | 棋盘舞台：浮动操作条（上下槽 / 横屏竖轨）、棋盘、悬浮数字面板、暂停遮挡 |
 * | GameFunctionArea.kt | 功能区：数字键（横排 / 九宫格）+ 偏好开关 + 设置入口 |
 * | GameKeyHandling.kt | 桌面键盘映射 handleKeyEvent 与数字键识别 |
 * | GameWinDrawer.kt | 通关结算抽屉 |
 *
 * 拆分只搬位置、不改逻辑（886 行代码逐行等价），跨文件可见性由 private 提为 internal。
 */

/** 抽屉标识：通关结算（模态，必须明确选择）。 */
private const val DRAWER_WIN = "game.win"

/** 抽屉标识：键位设置（可 Esc / 点遮罩关闭）。 */
private const val DRAWER_KEYMAP = "game.keymap"

/** 抽屉标识：游戏设置（窄屏时四项偏好收进抽屉，不占常驻布局空间）。 */
private const val DRAWER_TOGGLES = "game.toggles"

/** 抽屉标识：看答案确认（模态——它会**结束本局**，必须明确选择）。 */
private const val DRAWER_REVEAL = "game.reveal"

/**
 * 棋盘之外那层可点空白的测试标签。
 *
 * 它只在测试里被点到——UI 测试没法"点一个没有节点的地方"，而"点空白处应该清掉选中"
 * 这条行为必须能自动验收（靠肉眼看高亮有没有消失太不可靠）。
 */
internal const val BoardBackdropTag = "game.boardBackdrop"

/**
 * 「看答案」确认按钮的测试标签。
 *
 * 必须给标签而不是按文案找：抽屉标题与按钮都叫「看答案」，按文案会命中两个节点
 * （`assertIsDisplayed` 能过，但 `performMouseInput` 会因"期望恰好一个"而失败）。
 */
internal const val RevealConfirmTag = "game.revealConfirm"


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
     * 看答案确认抽屉的键：`Enter` / 空格 = 确认看答案，`Esc` = 取消（可关，不强制）。
     *
     * 抽屉本身是模态的，其余按键由 [rememberTopDrawerController] 统一吞掉——
     * 因此"打开确认框后误按数字键落子"不会发生。
     */
    val revealDrawerKeys: (KeyEvent) -> Boolean = { event ->
        when (event.key) {
            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                drawer.close()
                dispatch(GameAction.Reveal)
                true
            }
            Key.Escape -> {
                drawer.close()
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
                !state.keyMap.accepts(token) -> {
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
        state.language,
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
                        drawer.isOpen(DRAWER_REVEAL) -> revealDrawerKeys(e)
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
        // 横屏：棋盘左 + 键盘右双栏（右栏较窄，键盘用九宫格 + 开关进抽屉）
        val landscape = gameLayout(maxWidth, maxHeight) == GameLayout.Landscape
        // 键盘切九宫格的判据：竖屏窄机 或 横屏（右栏宽度有限，横排 1–9 塞不下）
        val keypadGrid = narrow || landscape
        // 功能区的高度（仅竖屏用）：数字键一行/九宫格 + 偏好开关一行/设置按钮，
        // 棋盘必须避开它——横屏时功能区改到右栏、不再占棋盘下方的纵向空间
        val keypadHeight =
            if (keypadGrid) DesignTokens.Sizes.KeyHeight * 3 + DesignTokens.Spacing.Sm * 2
            else DesignTokens.Sizes.KeyHeight
        // 笔记块（双键盘的第二块）需要的高度：九宫格 3×3 用 NoteKeyHeight，横排一行
        // 笔记块是**单行**（1–9 一行，见 NumberPad）：纵向只吃 40dp，
        // 这是它在手机竖屏与横屏都放得下的关键（3×3 要 128dp，实测会挤掉底部按钮）
        val noteBlockHeight = DesignTokens.Sizes.NoteKeyHeight
        val baseFunctionsHeight =
            keypadHeight + DesignTokens.Spacing.Sm + DesignTokens.Sizes.CompactItemHeight
        // **双键盘是否放得下**：只在"加上笔记块之后，棋盘仍不低于 BoardMinSize"时才启用。
        //
        // 这是本轮唯一需要预算的地方：小屏（360×640）实测把笔记块塞进去后，
        // 主键盘与「游戏设置」被挤出屏幕（渲染验证过）。所以不无条件加，
        // 而是先按"没有笔记块"的预算算棋盘，再看剩余高度够不够再放一块。
        val noteRowBudget = noteBlockHeight + DesignTokens.Spacing.Xs
        val reservedBase =
            DesignTokens.Sizes.TopBarHeight +
                pad * 2 +
                DesignTokens.Sizes.FloatingBarSlot * 2 +
                pad +
                baseFunctionsHeight +
                DesignTokens.Sizes.KeyHintHeight * (if (narrow) 2 else 1) +
                DesignTokens.Sizes.SnackbarReserve
        val spareHeight = maxHeight - reservedBase - DesignTokens.Sizes.BoardMinSize
        // 横屏**同样要判**：右栏纵向只有 411dp，主键盘 172dp + 笔记块 132dp + 抽屉按钮 +
        // 键位提示会超（渲染验证：笔记块被截断在屏幕外）。
        // 横屏的可用高度是 maxHeight（右栏独占一整列），因此同一套判据直接适用。
        val showNotePad = if (landscape) {
            val spareLandscape = maxHeight - (DesignTokens.Sizes.TopBarHeight + pad * 2 + baseFunctionsHeight + DesignTokens.Sizes.KeyHintHeight * 2)
            spareLandscape >= noteRowBudget
        } else {
            spareHeight >= noteRowBudget
        }
        val functionsHeight = baseFunctionsHeight + if (showNotePad) noteRowBudget else 0.dp
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
        // 横屏：棋盘边长由**高度**主导（宽是富余的），并给右栏 + 左侧浮动竖轨留出固定宽度。
        //
        // 与竖屏的关键差别：**不留上下的浮动条槽**。横屏纵向最稀缺，两条 48dp 的槽要吃掉 96dp；
        // 实测 891×411dp 时棋盘因此被压到 BoardMinSize 下限 160dp（单格 17dp，几乎点不准），
        // 比竖屏的 232dp 还小 —— 与"横屏腾出纵向空间"的初衷正好相反。
        // 现在把撤销 / 重做挪到棋盘左侧的**竖轨**（[BarRail]），用横向富余换纵向。
        val reservedLandscape =
            DesignTokens.Sizes.TopBarHeight +
                pad * 2 +
                DesignTokens.Sizes.KeyHintHeight +
                DesignTokens.Sizes.SnackbarReserve
        val boardSide = if (landscape) {
            minOf(
                (maxHeight - reservedLandscape).coerceAtLeast(DesignTokens.Sizes.BoardMinSize),
                (
                    maxWidth - pad * 2 - pad - DesignTokens.Sizes.LandscapeControlWidth -
                        DesignTokens.Sizes.FloatingBarSlot - pad
                    ).coerceAtLeast(DesignTokens.Sizes.BoardMinSize),
            )
        } else {
            minOf(
                maxWidth - pad * 2,
                (maxHeight - reserved).coerceAtLeast(DesignTokens.Sizes.BoardMinSize),
            )
        }

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
                onOpenReveal = { drawer.open(DRAWER_REVEAL) },
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
                if (landscape) {
                    // 横屏双栏：棋盘左 + 键盘右（右栏较窄，键盘用九宫格 + 开关进设置抽屉）；
                    // 键位提示压在内容底部、占整页宽。棋盘边长由高度主导，题面四周仍留白。
                    Column(modifier = Modifier.fillMaxSize().padding(pad)) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // 左侧竖轨：撤销 / 重做。横屏不占上下的槽（见 reservedLandscape 注释）
                            BarRail(strings = strings, state = state, onAction = dispatch)
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
                                // 横屏不留上下浮动条槽：纵向让给棋盘，条改在左侧竖轨
                                reserveBarSlots = false,
                                modifier = Modifier.width(boardSide),
                            )
                            Spacer(Modifier.width(pad))
                            FunctionArea(
                                strings = strings,
                                game = game,
                                state = state,
                                narrow = true,
                                showNotePad = showNotePad,
                                onOpenToggles = { drawer.open(DRAWER_TOGGLES) },
                                onAction = dispatch,
                                modifier = Modifier.width(DesignTokens.Sizes.LandscapeControlWidth),
                            )
                        }
                        KeyHintRow(strings = strings, keyMap = state.keyMap, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    // 竖屏：纵向一列——棋盘 → 数字键 → 偏好开关，整体居中，唯一中轴。
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
                            showNotePad = showNotePad,
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

        // 看答案确认抽屉：会**结束本局**（记一次放弃、连胜清零、不计胜场），
        // 因此与"通关抽屉"同样是模态的，但允许 Esc / 点遮罩取消——玩家打开它只是想确认后果。
        TopDrawer(
            visible = drawer.isOpen(DRAWER_REVEAL),
            onDismiss = { drawer.close() },
            restoreFocus = focusRequester,
            a11yTitle = strings.revealTitle,
        ) {
            InkText(
                text = strings.revealTitle,
                modifier = Modifier.fillMaxWidth(),
                style = Ink.Type.Title,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.Spacing.Sm))
            InkText(
                text = strings.revealNote,
                modifier = Modifier.fillMaxWidth(),
                style = Ink.Type.Caption.copy(color = Ink.Light),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.Spacing.Lg))
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md)) {
                InkButton(
                    text = strings.cancel,
                    onClick = { drawer.close() },
                    modifier = Modifier.weight(1f),
                    compact = true,
                )
                InkButton(
                    text = strings.revealAnswer,
                    onClick = {
                        drawer.close()
                        dispatch(GameAction.Reveal)
                    },
                    modifier = Modifier.weight(1f).testTag(RevealConfirmTag),
                    compact = true,
                    emphasized = true,
                )
            }
        }

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
            // 窄屏专属：「看答案」入口（宽屏它在顶栏）。先关本抽屉再开确认抽屉——
            // 一个控制器只能同时开一个抽屉，不先关会出现"开了没反应"。
            Spacer(Modifier.height(DesignTokens.Spacing.Md))
            InkButton(
                text = strings.revealAnswer,
                onClick = {
                    drawer.close()
                    drawer.open(DRAWER_REVEAL)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.interactive,
                compact = true,
            )
        }
    }
}
