package org.example.sudoku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.example.sudoku.core.Game
import org.example.sudoku.core.Sudoku
import org.example.sudoku.state.GameAction
import org.example.sudoku.state.GameState
import org.example.sudoku.ui.components.BarSide
import org.example.sudoku.ui.components.BoardCanvas
import org.example.sudoku.ui.components.FloatingBarPolicy
import org.example.sudoku.ui.components.FloatingInputPad
import org.example.sudoku.ui.components.InkButton
import org.example.sudoku.ui.components.InkIcon
import org.example.sudoku.ui.components.InkIconButton
import org.example.sudoku.ui.components.InkPanel
import org.example.sudoku.ui.components.InkText
import org.example.sudoku.ui.i18n.Strings
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/*
 * 棋盘舞台：浮动操作条（竖屏上下两槽 / 横屏左侧竖轨）+ 棋盘 + 悬浮数字面板 + 暂停遮挡。
 *
 * 从 GameScreen.kt 拆出（2026-10-24）：只搬位置、不改逻辑。
 */

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
internal fun BoardStage(
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
    /**
     * 是否在棋盘上 / 下留出浮动条的两条槽。
     *
     * **横屏传 false**：那时纵向是最稀缺的资源，两条 48dp 的槽要吃掉 96dp——实测手机横屏
     * （891×411dp）里棋盘因此被压到 `BoardMinSize` 下限 160dp（单格 17dp，几乎点不准）。
     * 横屏改由调用方把撤销 / 重做挂到棋盘**左侧的竖轨**上：横向本来就富余，用宽度换高度，
     * 而且竖轨同样不会压住题面（与"条绝不挡棋盘"这条不变量一致）。
     */
    reserveBarSlots: Boolean = true,
) {
    var barSide by remember { mutableStateOf(FloatingBarPolicy.Default) }
    // 棋盘容器高度单独记账：**不要在 pointerInput 协程里直接读 `size.height`**——
    // 协程在首次布局之前就启动，那时读到的是 0；而它只在 key 变化时重启，
    // 于是会一直拿着 0（策略里 `boardHeight <= 0` = "量不到，保持原样"），表现为"条永远不动"。
    // 这个 bug 单测抓不到，只有在真机上把鼠标移进棋盘才会暴露。
    var boardHeight by remember { mutableIntStateOf(0) }

    Column(modifier = modifier) {
        if (reserveBarSlots) {
            BarSlot(visible = barSide == BarSide.Top) { UndoRedoBar(strings, state, onAction) }
        }

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

        if (reserveBarSlots) {
            BarSlot(visible = barSide == BarSide.Bottom) { UndoRedoBar(strings, state, onAction) }
        }
    }
}

/** 棋盘上 / 下为浮动条预留的槽：条只在这一条带子里出现，绝不压住题面。 */
@Composable
internal fun BarSlot(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(DesignTokens.Sizes.FloatingBarSlot),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) content()
    }
}

/**
 * 横屏的**竖轨**：撤销 / 重做挂在棋盘左侧的窄列里。
 *
 * 为什么横屏要换成竖轨：横屏纵向最稀缺。原本上下两条 48dp 的槽要吃掉 96dp，
 * 实测 891×411dp 里棋盘被压到 160dp 下限（单格 17dp）。竖轨用**横向富余**换纵向，
 * 棋盘因此能拿回接近满高的边长；同时竖轨在棋盘之外，"条不挡题面"这条不变量依然成立。
 *
 * 宽度取 [DesignTokens.Sizes.FloatingBarSlot]：与原来的槽同宽，撤销 / 重做按钮（36dp）
 * 居中放得下，命中区仍由 `clickable` 自动扩到 48dp。
 */
@Composable
internal fun BarRail(strings: Strings, state: GameState, onAction: (GameAction) -> Unit) {
    Box(
        modifier = Modifier
            .width(DesignTokens.Sizes.FloatingBarSlot)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        // 竖着排：撤销在上、重做在下，与"上一条、下一条"的阅读顺序一致
        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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

/**
 * 撤销 / 重做：贴在棋盘上或下的浮动条，位置由 [FloatingBarPolicy] 跟着鼠标决定。
 *
 * 两个按钮的可用性直接跟着撤销 / 重做栈走——栈空即禁用，不用额外状态。
 */
@Composable
internal fun UndoRedoBar(
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
internal fun PauseVeil(strings: Strings, onAction: (GameAction) -> Unit) {
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

