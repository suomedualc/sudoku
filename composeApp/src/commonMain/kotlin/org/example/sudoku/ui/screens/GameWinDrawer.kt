package org.example.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import org.example.sudoku.core.Sudoku
import org.example.sudoku.ui.components.InkButton
import org.example.sudoku.ui.components.InkText
import org.example.sudoku.ui.components.InkTitleFrame
import org.example.sudoku.ui.components.TopDrawer
import org.example.sudoku.ui.i18n.Strings
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/*
 * 通关结算抽屉：成绩三行 + 再来一局 / 返回首页。
 *
 * 从 GameScreen.kt 拆出（2026-10-24）：只搬位置、不改逻辑。
 */

/**
 * 通关抽屉：用 [TopDrawer] 从顶部滑下（自带滑入动画），给出本局成绩并支持直接再来一局。
 * 不可点遮罩关闭（[TopDrawer] 的 `dismissible = false`）——避免"随手一点就丢了结算信息"。
 */
@Composable
internal fun WinDrawer(
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
internal fun WinRow(label: String, value: String) {
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

