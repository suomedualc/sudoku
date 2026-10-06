package org.example.sudoku

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import org.example.sudoku.ui.screens.GameScreen
import org.example.sudoku.ui.screens.MenuScreen
import org.example.sudoku.ui.theme.Ink
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 应用入口：装配主题、页面骨架与两个页面（首页 / 对局）。
 *
 * 视觉语言是「手写纸 · 简约油墨」，因此：
 * - 固定使用 miuix 的**浅色纸面**配色（不跟随系统深色；夜间墨色版本见 `docs/05-发展规划.md`）；
 * - 页面内容自绘墨线组件，miuix 只负责骨架（`Scaffold` 的安全区与 `SnackbarHost` 的提示通道）；
 * - 背景统一铺 [Ink.Paper]，让纸面从状态栏一直延伸到页面底部。
 *
 * 数据流仍是单向：`输入 → GameAction → GameReducer → Reduction(state, message) → 重组`；
 * 副作用只有三处：提示走**事件通道** → Snackbar、计时由心跳按**真实时间**同步、存档由 `GameStore` 落盘。
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
    MiuixTheme(colors = lightColorScheme()) {
        val viewModel = remember(store) { GameViewModel(store = store) }
        val state = viewModel.state
        val snackbarState = remember { SnackbarHostState() }

        // 计时心跳：约 250ms 校对一次；真正的秒数由单调时钟算出（不再"每秒 +1"，长局不漂移）
        LaunchedEffect(Unit) {
            while (true) {
                delay(250)
                viewModel.syncClock()
            }
        }

        // 一次性提示：从事件通道逐条取；相同文案也会逐条送达（旧实现会被等值比较吞掉）
        LaunchedEffect(Unit) {
            for (message in viewModel.messages) {
                snackbarState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
        }

        // 窗口最小化 / 切后台 → 自动暂停
        LaunchedEffect(isWindowActive) {
            if (!isWindowActive) viewModel.dispatch(GameAction.Pause)
        }

        Scaffold(
            snackbarHost = { SnackbarHost(state = snackbarState) },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink.Paper)
                    // 完整应用 Scaffold 的 contentPadding（顶部栏高度 + 系统栏 / 刘海安全区）
                    .padding(paddingValues),
            ) {
                when (state.screen) {
                    Screen.Menu -> MenuScreen(
                        canResume = viewModel.canResume,
                        onStart = { viewModel.dispatch(GameAction.NewGame(it)) },
                        onResume = { viewModel.dispatch(GameAction.Navigate(Screen.Game)) },
                        onExit = onExit,
                    )

                    Screen.Game -> GameScreen(
                        state = state,
                        conflicts = viewModel.conflicts,
                        progress = viewModel.progress,
                        onAction = viewModel::dispatch,
                    )
                }
            }
        }
    }
}
