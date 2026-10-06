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
 * 数据流仍是单向：`输入 → GameAction → GameReducer → GameState → 重组`；
 * 计时由 1 秒心跳派发 `Tick`；一次性提示经 `state.message` 转 Snackbar。
 *
 * @param store 存档端口（桌面注入文件实现，Android / iOS 各自提供）；默认不落盘。
 * @param onExit 「退出游戏」的回调（桌面 = 关窗退出；移动端可传空实现）。
 */
@Composable
fun App(
    store: GameStore = NoopGameStore,
    onExit: () -> Unit = {},
) {
    MiuixTheme(colors = lightColorScheme()) {
        val viewModel = remember(store) { GameViewModel(store = store) }
        val state = viewModel.state
        val snackbarState = remember { SnackbarHostState() }

        // 计时心跳：每秒派发一次 Tick（仅在游戏进行且未暂停时累加）
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                viewModel.dispatch(GameAction.Tick)
            }
        }

        // 一次性提示：转为 Snackbar 后立即消费，避免重复弹出
        LaunchedEffect(state.message) {
            val message = state.message ?: return@LaunchedEffect
            snackbarState.showSnackbar(message, duration = SnackbarDuration.Short)
            viewModel.dispatch(GameAction.ConsumeMessage)
        }

        Scaffold(
            // 棋盘侧已预留 SnackbarReserve，无需再抬高提示条
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
