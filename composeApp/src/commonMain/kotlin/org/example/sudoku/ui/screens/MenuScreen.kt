package org.example.sudoku.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import org.example.sudoku.core.Difficulty
import org.example.sudoku.state.AppLanguage
import org.example.sudoku.state.GameStats
import org.example.sudoku.ui.components.InkButton
import org.example.sudoku.ui.components.InkGridSketch
import org.example.sudoku.ui.components.InkIcon
import org.example.sudoku.ui.components.InkIconButton
import org.example.sudoku.ui.components.InkText
import org.example.sudoku.ui.components.InkTitleFrame
import org.example.sudoku.ui.components.SentenceStrip
import org.example.sudoku.ui.components.rememberTopDrawerController
import org.example.sudoku.ui.i18n.stringsFor
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/** 主菜单入口的下标（顺序即上下排列顺序）。 */
private const val ENTRY_START = 0
private const val ENTRY_RESUME = 1
private const val ENTRY_STATS = 2
private const val ENTRY_LANGUAGE = 3
private const val ENTRY_EXIT = 4
private const val ENTRY_COUNT = 5

/**
 * 首页：手写纸 · 油墨风格。
 *
 * 五个入口：开始游戏 / 继续游戏 / 查看游玩统计 / 语言 / 退出游戏（单机游戏，无对弈、社交）。
 * - 开始游戏 → 难度抽屉，选完直接开局；继续游戏 → 未完成的对局（无存档置灰）；
 * - 查看游玩统计 / 语言 → 各自的抽屉（统计只读；语言切换当场生效，无需重启）；
 * - 退出游戏 → 二次确认。底部：随机句子（内置语料，定时 + 手动轮播）。
 *
 * 键盘：`↑` / `↓` 在入口之间移动（自动跳过置灰项），`Enter` / 空格 确认；
 * 抽屉打开时模态——所有按键先经 `TopDrawerKeys` 仲裁，未处理的键不会再落到菜单上。
 */
@Composable
fun MenuScreen(
    canResume: Boolean,
    stats: GameStats,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onStart: (Difficulty) -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit,
    darkMode: Boolean,
    onToggleDark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 全部可见文字从字典取词：语言是快照状态，切换即整页重组，无需重启
    val strings = stringsFor(language)
    val drawer = rememberTopDrawerController()
    val mainCursor = remember { MenuCursor() }
    val difficultyCursor = remember { MenuCursor() }
    val exitCursor = remember { MenuCursor() }
    val languageCursor = remember { MenuCursor() }
    val difficulties = remember { Difficulty.entries }
    val difficultyItemCount = difficulties.size + 1

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    /** 「继续游戏」没有存档时置灰，方向键需要跳过它。 */
    val mainEnabled: (Int) -> Boolean = { it != ENTRY_RESUME || canResume }

    val openDifficulty: () -> Unit = {
        difficultyCursor.reset()
        drawer.open(DRAWER_DIFFICULTY)
    }
    val activateMain: (Int) -> Unit = { index ->
        when (index) {
            ENTRY_START -> openDifficulty()
            ENTRY_RESUME -> if (canResume) onResume()
            ENTRY_STATS -> drawer.open(DRAWER_STATS)
            ENTRY_LANGUAGE -> openLanguageDrawer(languageCursor, drawer)
            else -> {
                exitCursor.reset()
                drawer.open(DRAWER_EXIT)
            }
        }
    }

    /** 抽屉内的键：方向键在条目间移动，Enter / 空格 确认（Esc 由抽屉控制器统一关闭）。 */
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

                DRAWER_LANGUAGE -> when {
                    delta != 0 -> {
                        languageCursor.move(delta, LANGUAGE_ITEM_COUNT)
                        true
                    }
                    confirm -> {
                        // 选完不关抽屉：让玩家当场看到整页文字换成新语言（"免重启"的直观证明）
                        onLanguageChange(AppLanguage.entries[languageCursor.index])
                        true
                    }
                    else -> false
                }

                else -> false
            }
        }
    }

    /** 页面本身的键（没有抽屉时）：只在入口之间移动 / 确认。 */
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
                // 先交给抽屉仲裁：抽屉打开时是模态的，未处理的键不会再落到菜单上
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
                title = strings.appTitle,
                subtitle = "SUDOKU",
                modifier = Modifier.widthIn(max = DesignTokens.Sizes.MenuMaxWidth),
            )
            // 随机句子：替代原来的"单机 · 无需联网"。放标题正下方而不是页面底部——
            // 底部在 900 高的窗口里会被折到首屏之外，"看一眼"的东西不该让玩家去滚动找
            Spacer(Modifier.height(DesignTokens.Spacing.Lg))
            SentenceStrip(strings = strings, modifier = Modifier.widthIn(max = DesignTokens.Sizes.MenuMaxWidth))
            Spacer(Modifier.height(DesignTokens.Spacing.Xxl))

            Column(
                modifier = Modifier
                    .widthIn(max = DesignTokens.Sizes.MenuMaxWidth)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md),
            ) {
                InkButton(
                    text = strings.menuStart,
                    onClick = {
                        mainCursor.select(ENTRY_START)
                        openDifficulty()
                    },
                    emphasized = true,
                    highlighted = mainCursor.index == ENTRY_START,
                )
                InkButton(
                    text = strings.menuResume,
                    onClick = {
                        mainCursor.select(ENTRY_RESUME)
                        onResume()
                    },
                    enabled = canResume,
                    highlighted = mainCursor.index == ENTRY_RESUME,
                )
                if (!canResume) {
                    InkText(
                        text = strings.menuNoResume,
                        modifier = Modifier.fillMaxWidth(),
                        style = Ink.Type.Meta.copy(color = Ink.Light),
                        textAlign = TextAlign.Center,
                    )
                }
                InkButton(
                    text = strings.viewStats,
                    onClick = {
                        mainCursor.select(ENTRY_STATS)
                        drawer.open(DRAWER_STATS)
                    },
                    highlighted = mainCursor.index == ENTRY_STATS,
                )
                InkButton(
                    text = strings.languageTitle + "：" + language.label,
                    onClick = {
                        mainCursor.select(ENTRY_LANGUAGE)
                        openLanguageDrawer(languageCursor, drawer)
                    },
                    highlighted = mainCursor.index == ENTRY_LANGUAGE,
                )
                InkButton(
                    text = strings.menuExit,
                    onClick = {
                        mainCursor.select(ENTRY_EXIT)
                        exitCursor.reset()
                        drawer.open(DRAWER_EXIT)
                    },
                    highlighted = mainCursor.index == ENTRY_EXIT,
                )
                Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                InkText(
                    text = strings.menuKeys,
                    modifier = Modifier.fillMaxWidth(),
                    style = Ink.Type.Meta.copy(color = Ink.Light),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(DesignTokens.Spacing.Xxl))
            InkGridSketch()
            Spacer(Modifier.height(DesignTokens.Spacing.Xl))
        }

        // 右上角：明暗切换。与对局页顶栏用同一枚图标、同一个位置——切页时它不会"跳走"。
        InkIconButton(
            icon = if (darkMode) InkIcon.Sun else InkIcon.Moon,
            contentDescription = if (darkMode) strings.toLight else strings.toNight,
            tooltip = if (darkMode) strings.tooltipPaper else strings.tooltipNight,
            onClick = onToggleDark,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(DesignTokens.Spacing.Md),
            tooltipAlignment = Alignment.TopEnd,
        )

        MenuDrawers(
            drawer = drawer,
            strings = strings,
            difficulties = difficulties,
            difficultyCursor = difficultyCursor,
            exitCursor = exitCursor,
            languageCursor = languageCursor,
            language = language,
            stats = stats,
            focusRequester = focusRequester,
            onStart = onStart,
            onLanguageChange = onLanguageChange,
            onExit = onExit,
        )
    }
}

/**
 * 键盘高亮下标：`move` 在给定条目数内循环移动，可跳过置灰项
 * （若全部不可用则原地不动）。只保存"当前项"，渲染交给调用方。
 */
internal class MenuCursor {
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
internal fun isConfirmKey(key: Key): Boolean =
    key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar
