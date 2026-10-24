package org.example.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import org.example.sudoku.core.Game
import org.example.sudoku.core.Sudoku
import org.example.sudoku.state.GameAction
import org.example.sudoku.state.GameState
import org.example.sudoku.state.Screen
import org.example.sudoku.ui.components.InkIcon
import org.example.sudoku.ui.components.InkIconButton
import org.example.sudoku.ui.components.InkText
import org.example.sudoku.ui.i18n.Strings
import org.example.sudoku.ui.i18n.stringsFor
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/*
 * 对局页顶栏：返回菜单、难度与读数、提示 / 暂停 / 重置 / 明暗切换。
 *
 * 从 GameScreen.kt 拆出（2026-10-24）：只搬位置、不改逻辑。
 */

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
internal fun GameTopBar(
    game: Game,
    state: GameState,
    done: Int,
    total: Int,
    narrow: Boolean,
    onAction: (GameAction) -> Unit,
    /** 打开键位设置抽屉（抽屉状态在页面里，这里只发信号）。 */
    onOpenKeyMap: () -> Unit,
    /** 打开「看答案」确认抽屉（同上：抽屉状态在页面里）。 */
    onOpenReveal: () -> Unit,
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
        // 新按钮一律加在**这一组的最前面**（向左生长）：整组是右对齐的，因此已有按钮的
        // 横坐标全部不变——明暗切换仍在最右（冒烟脚本按固定坐标点它，`tools/smoke-e2e.ps1`）。
        //
        // 「看答案」**只在宽屏放进顶栏**：它是**低频且会结束本局**的动作（记一次放弃），
        // 放在最前与高频的提示 / 暂停 / 重置拉开距离，避免误触。
        // 窄屏（< 460dp）改由「游戏设置」抽屉提供入口 —— 顶栏已有 5 枚图标按钮，
        // 再加一枚会把中间读数区从 ~123dp 压到 ~63dp，正是 docs/08 §4-D1 那条截断缺陷。
        if (!narrow) {
            InkIconButton(
                icon = InkIcon.Reveal,
                contentDescription = strings.revealAnswer,
                onClick = onOpenReveal,
                enabled = state.interactive,
                tooltipAlignment = Alignment.TopEnd,
            )
        }
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

