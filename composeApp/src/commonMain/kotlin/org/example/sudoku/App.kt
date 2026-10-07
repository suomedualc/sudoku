package org.example.sudoku

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import org.example.sudoku.state.GameAction
import org.example.sudoku.state.GameStore
import org.example.sudoku.state.GameViewModel
import org.example.sudoku.state.NoopGameStore
import org.example.sudoku.state.Screen
import org.example.sudoku.ui.components.InkSnackbarHost
import org.example.sudoku.ui.components.InkSnackbarHostState
import org.example.sudoku.ui.i18n.stringsFor
import org.example.sudoku.ui.screens.GameScreen
import org.example.sudoku.ui.theme.safeAreaPadding
import org.example.sudoku.ui.screens.MenuScreen
import org.example.sudoku.ui.theme.Ink

/**
 * 应用入口：装配页面骨架与两个页面（首页 / 对局）。
 *
 * 视觉语言是「手写纸 · 简约油墨」，因此：
 * - **没有第三方组件库**：骨架是 `Box` + `Modifier.safeAreaPadding()`（安全区按平台声明，
 *   桌面恒为 0），提示是自绘墨条，
 *   其余全部自绘墨线组件（详见 `docs/02-设计规范.md` §2）；
 * - 背景统一铺 [Ink.Paper]，让纸面从状态栏一直延伸到页面底部。
 *
 * 数据流仍是单向：`输入 → GameAction → GameReducer → Reduction(state, message) → 重组`；
 * 副作用只有三处：提示走**事件通道** → 墨条、计时由心跳按**真实时间**同步、存档由 `GameStore` 落盘。
 *
 * @param store 存档端口（桌面注入文件实现，Android / iOS 各自提供）；默认不落盘。
 * @param onExit 「退出游戏」的回调（桌面 = 关窗退出；移动端可传空实现）。
 * @param isWindowActive 窗口是否处于活动状态（桌面传 `!isMinimized`，移动端可接生命周期）；
 *                       变为 false 时自动暂停，避免"挂后台还在走表"。
 */
@Composable
fun App(
    store: GameStore = NoopGameStore,
    onExit: () -> Unit = {},
    isWindowActive: Boolean = true,
) {
    val viewModel = remember(store) { GameViewModel(store = store) }
    val state = viewModel.state
    val snackbarState = remember { InkSnackbarHostState() }

    // 主题同步：把偏好推给 Ink。放在**最前面**，保证同一帧里后面所有子组件读到的都是新配色，
    // 不会出现"先按浅色画一帧再翻成夜墨"的闪烁；值没变时不写，避免无谓的状态失效。
    if (Ink.isDark != state.darkMode) Ink.setDark(state.darkMode)

    // 计时心跳：约 250ms 校对一次；真正的秒数由单调时钟算出（不再"每秒 +1"，长局不漂移）
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            viewModel.syncClock()
        }
    }

    // 一次性提示：从事件通道逐条取，**按当前语言渲染**再弹墨条。
    // 显示是**顶替式**：新提示立即顶掉当前那条并独占固定时长（计时在 InkSnackbarHost 的协程里）——
    // 连续快速点击（如连按提示键）不会让墨条驻留时间随次数累加；相同文案也算新的一条
    // （自增 id），不会因等值比较被吞。语言在循环里现取（见下）。
    LaunchedEffect(Unit) {
        for (message in viewModel.messages) {
            snackbarState.show(stringsFor(viewModel.state.language).render(message))
        }
    }

    // 窗口最小化 / 切后台 → 自动暂停
    LaunchedEffect(isWindowActive) {
        if (!isWindowActive) viewModel.dispatch(GameAction.Pause)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Paper)
            // 安全区（系统栏 / 刘海）：桌面恒为 0，移动端由各平台 actual 提供，不需要组件库
            .safeAreaPadding(),
    ) {
        when (state.screen) {
            Screen.Menu -> MenuScreen(
                canResume = viewModel.canResume,
                stats = state.stats,
                language = state.language,
                onLanguageChange = { viewModel.dispatch(GameAction.SetLanguage(it)) },
                onStart = { viewModel.dispatch(GameAction.NewGame(it)) },
                onResume = { viewModel.dispatch(GameAction.Navigate(Screen.Game)) },
                onExit = onExit,
                darkMode = state.darkMode,
                onToggleDark = { viewModel.dispatch(GameAction.ToggleDarkMode) },
            )

            Screen.Game -> GameScreen(
                state = state,
                conflicts = viewModel.conflicts,
                progress = viewModel.progress,
                onAction = viewModel::dispatch,
            )
        }

        // 墨条盖在页面之上：放在最后 = 在最上层；它不带 clickable / pointerInput，不会吃掉点击
        InkSnackbarHost(snackbarState)
    }
}
