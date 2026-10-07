package org.example.sudoku.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import org.example.sudoku.core.Difficulty
import org.example.sudoku.ui.components.InkButton
import org.example.sudoku.ui.components.InkDivider
import org.example.sudoku.ui.components.InkGridSketch
import org.example.sudoku.ui.components.InkIcon
import org.example.sudoku.ui.components.InkIconButton
import org.example.sudoku.ui.components.InkText
import org.example.sudoku.ui.components.InkTitleFrame
import org.example.sudoku.ui.components.TopDrawer
import org.example.sudoku.ui.components.rememberTopDrawerController
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/** 主菜单三入口的下标（顺序即上下排列顺序）。 */
private const val ENTRY_START = 0
private const val ENTRY_RESUME = 1
private const val ENTRY_EXIT = 2
private const val ENTRY_COUNT = 3

/** 退出确认：0 = 取消、1 = 退出。 */
private const val EXIT_CANCEL = 0
private const val EXIT_QUIT = 1
private const val EXIT_ITEM_COUNT = 2

/** 抽屉标识（每个抽屉一个常量，避免字符串写错）。 */
private const val DRAWER_DIFFICULTY = "menu.difficulty"
private const val DRAWER_EXIT = "menu.exit"

/**
 * 首页：手写纸 · 油墨风格。
 *
 * 结构参考"标题框 + 竖排主菜单 + 底部棋盘插图"的经典布局，但只用墨线与留白表达；
 * **只有三个入口**：开始游戏 / 继续游戏 / 退出游戏（单机游戏，无对弈、联网、社交）。
 *
 * - 开始游戏 → 顶部抽屉里的难度选择（简单 / 普通 / 困难 / 大师），选完直接开局；
 * - 继续游戏 → 进入未完成的对局（没有存档时置灰，并给出文字说明）；
 * - 退出游戏 → 顶部抽屉二次确认后退出。
 *
 * 键盘（桌面）：`↑` / `↓` 在三入口之间移动——**自动跳过置灰项**，与棋盘方向键"跳过给定格"
 * 是同一套约定；`Enter` / 空格 确认。抽屉（难度 / 退出确认）同样支持方向键选择、
 * `Enter` 确认、`Esc` 关闭，鼠标点击也会把高亮同步过去，两种输入方式不打架。
 *
 * 抽屉打开时它是**模态**的：所有按键先经 [rememberTopDrawerController] 仲裁，
 * 未处理的键不会再落到菜单上（详见 `TopDrawerKeys`）。
 */
@Composable
fun MenuScreen(
    canResume: Boolean,
    onStart: (Difficulty) -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit,
    darkMode: Boolean,
    onToggleDark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawer = rememberTopDrawerController()
    val mainCursor = remember { MenuCursor() }
    val difficultyCursor = remember { MenuCursor() }
    val exitCursor = remember { MenuCursor() }
    val difficulties = remember { Difficulty.entries }
    // 难度抽屉的最后一项是「返回」
    val difficultyItemCount = difficulties.size + 1

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    /** 「继续游戏」没有存档时置灰，方向键需要跳过它。 */
    val mainEnabled: (Int) -> Boolean = { it != ENTRY_RESUME || canResume }

    val openDifficulty: () -> Unit = {
        difficultyCursor.reset()
        drawer.open(DRAWER_DIFFICULTY)
    }
    val openExitConfirm: () -> Unit = {
        exitCursor.reset()
        drawer.open(DRAWER_EXIT)
    }
    val activateMain: (Int) -> Unit = { index ->
        when (index) {
            ENTRY_START -> openDifficulty()
            ENTRY_RESUME -> if (canResume) onResume()
            else -> openExitConfirm()
        }
    }

    /** 抽屉内的键：方向键在抽屉条目间移动，Enter / 空格 确认（Esc 由抽屉控制器统一关闭）。 */
    val drawerKeys: (KeyEvent) -> Boolean = { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            val delta = verticalDelta(event.key)
            val confirm = isConfirmKey(event.key)
            when (drawer.activeId) {
                DRAWER_EXIT -> when {
                    delta != 0 -> {
                        exitCursor.move(delta, EXIT_ITEM_COUNT)
                        true
                    }
                    confirm -> {
                        val quit = exitCursor.index == EXIT_QUIT
                        drawer.close()
                        if (quit) onExit()
                        true
                    }
                    else -> false
                }

                DRAWER_DIFFICULTY -> when {
                    delta != 0 -> {
                        difficultyCursor.move(delta, difficultyItemCount)
                        true
                    }
                    confirm -> {
                        val index = difficultyCursor.index
                        drawer.close()
                        if (index < difficulties.size) onStart(difficulties[index])
                        true
                    }
                    else -> false
                }

                else -> false
            }
        }
    }

    /** 页面本身的键（没有抽屉时）：只在三入口之间移动 / 确认。 */
    val pageKeys: (KeyEvent) -> Boolean = { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            val delta = verticalDelta(event.key)
            when {
                delta != 0 -> {
                    mainCursor.move(delta, ENTRY_COUNT, mainEnabled)
                    true
                }
                isConfirmKey(event.key) -> {
                    activateMain(mainCursor.index)
                    true
                }
                else -> false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                // 先交给抽屉仲裁：抽屉打开时是模态的，未处理的键不会再落到菜单上（避免"两面同时响应"）
                drawer.handleKey(event, drawerKeys) || pageKeys(event)
            }
            .focusable(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.Spacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(DesignTokens.Spacing.Xxl))

            InkTitleFrame(
                title = "数独",
                subtitle = "SUDOKU",
                modifier = Modifier.widthIn(max = DesignTokens.Sizes.MenuMaxWidth),
            )

            Spacer(Modifier.height(DesignTokens.Spacing.Xxl))

            Column(
                modifier = Modifier
                    .widthIn(max = DesignTokens.Sizes.MenuMaxWidth)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md),
            ) {
                InkButton(
                    text = "开始游戏",
                    onClick = {
                        mainCursor.select(ENTRY_START)
                        openDifficulty()
                    },
                    emphasized = true,
                    highlighted = mainCursor.index == ENTRY_START,
                )
                InkButton(
                    text = "继续游戏",
                    onClick = {
                        mainCursor.select(ENTRY_RESUME)
                        onResume()
                    },
                    enabled = canResume,
                    highlighted = mainCursor.index == ENTRY_RESUME,
                )
                if (!canResume) {
                    InkText(
                        text = "暂无未完成的对局",
                        modifier = Modifier.fillMaxWidth(),
                        style = Ink.Type.Meta.copy(color = Ink.Light),
                        textAlign = TextAlign.Center,
                    )
                }
                InkButton(
                    text = "退出游戏",
                    onClick = {
                        mainCursor.select(ENTRY_EXIT)
                        openExitConfirm()
                    },
                    highlighted = mainCursor.index == ENTRY_EXIT,
                )
                Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                InkText(
                    text = "↑↓ 选择 · Enter 确认",
                    modifier = Modifier.fillMaxWidth(),
                    style = Ink.Type.Meta.copy(color = Ink.Light),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(DesignTokens.Spacing.Xxl))

            InkGridSketch()

            Spacer(Modifier.height(DesignTokens.Spacing.Lg))

            InkText(text = "单机 · 无需联网", style = Ink.Type.Meta.copy(color = Ink.Light))

            Spacer(Modifier.height(DesignTokens.Spacing.Xl))
        }

        // 右上角：明暗切换。与对局页顶栏用同一枚图标、同一个位置——切页时它不会"跳走"。
        InkIconButton(
            icon = if (darkMode) InkIcon.Sun else InkIcon.Moon,
            contentDescription = if (darkMode) "切回浅色纸面" else "切换到夜墨模式",
            // 悬停提示用短名；贴右边缘因此提示片向左长
            tooltip = if (darkMode) "纸面模式" else "夜墨模式",
            onClick = onToggleDark,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(DesignTokens.Spacing.Md),
            tooltipAlignment = Alignment.TopEnd,
        )

        TopDrawer(
            visible = drawer.isOpen(DRAWER_DIFFICULTY),
            onDismiss = { drawer.close() },
            restoreFocus = focusRequester,
            a11yTitle = "选择难度",
        ) {
            InkText(
                text = "选择难度",
                modifier = Modifier.fillMaxWidth(),
                style = Ink.Type.Title,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.Spacing.Md))
            InkDivider(seed = 31)
            Spacer(Modifier.height(DesignTokens.Spacing.Md))
            difficulties.forEachIndexed { index, difficulty ->
                InkButton(
                    text = "${difficulty.label}　${difficulty.targetBlanks} 空",
                    onClick = {
                        drawer.close()
                        onStart(difficulty)
                    },
                    compact = true,
                    highlighted = difficultyCursor.index == index,
                )
                Spacer(Modifier.height(DesignTokens.Spacing.Sm))
            }
            Spacer(Modifier.height(DesignTokens.Spacing.Xs))
            InkButton(
                text = "返回",
                onClick = { drawer.close() },
                compact = true,
                highlighted = difficultyCursor.index == difficulties.size,
            )
            Spacer(Modifier.height(DesignTokens.Spacing.Sm))
            InkText(
                text = "↑↓ 选择 · Enter 确认 · Esc 返回",
                modifier = Modifier.fillMaxWidth(),
                style = Ink.Type.Meta.copy(color = Ink.Light),
                textAlign = TextAlign.Center,
            )
        }

        TopDrawer(
            visible = drawer.isOpen(DRAWER_EXIT),
            onDismiss = { drawer.close() },
            restoreFocus = focusRequester,
            a11yTitle = "退出确认",
        ) {
            InkText(
                text = "退出游戏？",
                modifier = Modifier.fillMaxWidth(),
                style = Ink.Type.Title,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.Spacing.Sm))
            InkText(
                text = "未完成的对局会自动保存",
                modifier = Modifier.fillMaxWidth(),
                style = Ink.Type.Caption.copy(color = Ink.Light),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.Spacing.Lg))
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md)) {
                InkButton(
                    text = "取消",
                    onClick = { drawer.close() },
                    modifier = Modifier.weight(1f),
                    compact = true,
                    highlighted = exitCursor.index == EXIT_CANCEL,
                )
                InkButton(
                    text = "退出",
                    onClick = {
                        drawer.close()
                        onExit()
                    },
                    modifier = Modifier.weight(1f),
                    compact = true,
                    emphasized = true,
                    highlighted = exitCursor.index == EXIT_QUIT,
                )
            }
        }
    }
}

/**
 * 键盘高亮下标：`move` 在给定条目数内循环移动，可跳过置灰项
 * （若全部不可用则原地不动）。只保存"当前项"，渲染交给调用方。
 */
private class MenuCursor {
    var index by mutableStateOf(0)
        private set

    fun move(delta: Int, size: Int, enabled: (Int) -> Boolean = { true }) {
        var next = index
        repeat(size) {
            next = (next + delta + size) % size
            if (enabled(next)) {
                index = next
                return
            }
        }
    }

    /** 鼠标点击后把高亮同步到被点的项，避免随后按 Enter 激活"另一个"入口。 */
    fun select(target: Int) {
        index = target
    }

    fun reset() {
        index = 0
    }
}

/** 方向键 → ±1；菜单是纵向的，所以左右键与上下键同义。 */
private fun verticalDelta(key: Key): Int = when (key) {
    Key.DirectionUp, Key.DirectionLeft -> -1
    Key.DirectionDown, Key.DirectionRight -> 1
    else -> 0
}

/** 确认键：Enter / 小键盘 Enter / 空格。 */
private fun isConfirmKey(key: Key): Boolean =
    key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar
