package org.example.sudoku.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import org.example.sudoku.core.Difficulty
import org.example.sudoku.ui.components.InkButton
import org.example.sudoku.ui.components.InkDivider
import org.example.sudoku.ui.components.InkGridSketch
import org.example.sudoku.ui.components.InkOverlay
import org.example.sudoku.ui.components.InkText
import org.example.sudoku.ui.components.InkTitleFrame
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/**
 * 首页：手写纸 · 油墨风格。
 *
 * 结构参考"标题框 + 竖排主菜单 + 底部棋盘插图"的经典布局，但只用墨线与留白表达；
 * **只有三个入口**：开始游戏 / 继续游戏 / 退出游戏（单机游戏，无对弈、联网、社交）。
 *
 * - 开始游戏 → 弹出墨框难度选择（简单 / 普通 / 困难 / 大师），选完直接开局；
 * - 继续游戏 → 进入未完成的对局（没有存档时置灰，并给出文字说明）；
 * - 退出游戏 → 二次确认后退出。
 */
@Composable
fun MenuScreen(
    canResume: Boolean,
    onStart: (Difficulty) -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDifficulty by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
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
                subtitle = "SUDOKU · 手写纸",
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
                    onClick = { showDifficulty = true },
                    emphasized = true,
                )
                InkButton(
                    text = "继续游戏",
                    onClick = onResume,
                    enabled = canResume,
                )
                if (!canResume) {
                    InkText(
                        text = "暂无未完成的对局",
                        modifier = Modifier.fillMaxWidth(),
                        style = Ink.style(12.sp, Ink.Light, letterSpacing = 2.sp),
                        textAlign = TextAlign.Center,
                    )
                }
                InkButton(
                    text = "退出游戏",
                    onClick = { showExitConfirm = true },
                )
            }

            Spacer(Modifier.height(DesignTokens.Spacing.Xxl))

            InkGridSketch()

            Spacer(Modifier.height(DesignTokens.Spacing.Lg))

            InkText(text = "单机 · 无需联网", style = Ink.style(12.sp, Ink.Light, letterSpacing = 3.sp))

            Spacer(Modifier.height(DesignTokens.Spacing.Xl))
        }

        if (showDifficulty) {
            InkOverlay(onDismiss = { showDifficulty = false }) {
                InkText(
                    text = "选择难度",
                    modifier = Modifier.fillMaxWidth(),
                    style = Ink.style(22.sp, Ink.Black, letterSpacing = 4.sp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(DesignTokens.Spacing.Md))
                InkDivider(seed = 31)
                Spacer(Modifier.height(DesignTokens.Spacing.Md))
                Difficulty.entries.forEach { difficulty ->
                    InkButton(
                        text = "${difficulty.label}　${difficulty.targetBlanks} 空",
                        onClick = {
                            showDifficulty = false
                            onStart(difficulty)
                        },
                        compact = true,
                    )
                    Spacer(Modifier.height(DesignTokens.Spacing.Sm))
                }
                Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                InkButton(
                    text = "返回",
                    onClick = { showDifficulty = false },
                    compact = true,
                )
            }
        }

        if (showExitConfirm) {
            InkOverlay(onDismiss = { showExitConfirm = false }) {
                InkText(
                    text = "退出游戏？",
                    modifier = Modifier.fillMaxWidth(),
                    style = Ink.style(22.sp, Ink.Black, letterSpacing = 4.sp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(DesignTokens.Spacing.Sm))
                InkText(
                    text = "未完成的对局会自动保存",
                    modifier = Modifier.fillMaxWidth(),
                    style = Ink.style(13.sp, Ink.Light),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(DesignTokens.Spacing.Lg))
                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md)) {
                    InkButton(
                        text = "取消",
                        onClick = { showExitConfirm = false },
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                    InkButton(
                        text = "退出",
                        onClick = {
                            showExitConfirm = false
                            onExit()
                        },
                        modifier = Modifier.weight(1f),
                        compact = true,
                        emphasized = true,
                    )
                }
            }
        }
    }
}
