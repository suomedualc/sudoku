package org.example.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import org.example.sudoku.core.Difficulty
import org.example.sudoku.state.AppLanguage
import org.example.sudoku.state.GameStats
import org.example.sudoku.ui.components.InkButton
import org.example.sudoku.ui.components.InkDivider
import org.example.sudoku.ui.components.InkText
import org.example.sudoku.ui.components.StatsSheet
import org.example.sudoku.ui.components.TopDrawer
import org.example.sudoku.ui.components.TopDrawerController
import org.example.sudoku.ui.i18n.Strings
import org.example.sudoku.ui.theme.Ink
import org.example.sudoku.ui.theme.DesignTokens

/** 抽屉标识（每个抽屉一个常量，避免字符串写错）。 */
internal const val DRAWER_DIFFICULTY = "menu.difficulty"
internal const val DRAWER_EXIT = "menu.exit"
internal const val DRAWER_STATS = "menu.stats"
internal const val DRAWER_LANGUAGE = "menu.language"

/** 退出确认：0 = 取消、1 = 退出。 */
internal const val EXIT_CANCEL = 0
internal const val EXIT_QUIT = 1
internal const val EXIT_ITEM_COUNT = 2

/** 语言抽屉：条目数 = 语言数。 */
internal val LANGUAGE_ITEM_COUNT = AppLanguage.entries.size

internal fun openLanguageDrawer(cursor: MenuCursor, drawer: TopDrawerController) {
    cursor.reset()
    drawer.open(DRAWER_LANGUAGE)
}

/** 首页的四个抽屉：难度 / 退出确认 / 游玩统计 / 语言。 */
@Composable
internal fun MenuDrawers(
    drawer: TopDrawerController,
    strings: Strings,
    difficulties: List<Difficulty>,
    difficultyCursor: MenuCursor,
    exitCursor: MenuCursor,
    languageCursor: MenuCursor,
    language: AppLanguage,
    stats: GameStats,
    focusRequester: FocusRequester,
    onStart: (Difficulty) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onExit: () -> Unit,
) {
    TopDrawer(
        visible = drawer.isOpen(DRAWER_DIFFICULTY),
        onDismiss = { drawer.close() },
        restoreFocus = focusRequester,
        a11yTitle = strings.chooseDifficulty,
    ) {
        InkText(
            text = strings.chooseDifficulty,
            modifier = Modifier.fillMaxWidth(),
            style = Ink.Type.Title,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Md))
        InkDivider(seed = 31)
        Spacer(Modifier.height(DesignTokens.Spacing.Md))
        difficulties.forEachIndexed { index, difficulty ->
            InkButton(
                text = strings.difficultyBlanks.format(strings.difficulty(difficulty), difficulty.targetBlanks),
                onClick = {
                    drawer.close()
                    onStart(difficulty)
                },
                compact = true,
                highlighted = difficultyCursor.index == index,
            )
            Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        }
        DrawerFooter(strings)
    }

    // 游玩统计：只读，数字是"履历"
    TopDrawer(
        visible = drawer.isOpen(DRAWER_STATS),
        onDismiss = { drawer.close() },
        restoreFocus = focusRequester,
        a11yTitle = strings.statsTitle,
    ) {
        StatsSheet(stats = stats, strings = strings)
        DrawerFooter(strings)
    }

    // 语言切换：选完不关抽屉，让玩家当场看到整页文字换成新语言（"免重启"的直观证明）
    TopDrawer(
        visible = drawer.isOpen(DRAWER_LANGUAGE),
        onDismiss = { drawer.close() },
        restoreFocus = focusRequester,
        a11yTitle = strings.languageTitle,
    ) {
        InkText(
            text = strings.languageTitle,
            modifier = Modifier.fillMaxWidth(),
            style = Ink.Type.Title,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Md))
        InkDivider(seed = 47)
        Spacer(Modifier.height(DesignTokens.Spacing.Md))
        AppLanguage.entries.forEachIndexed { index, option ->
            InkButton(
                text = option.label,
                onClick = { onLanguageChange(option) },
                compact = true,
                emphasized = option == language,
                highlighted = languageCursor.index == index,
            )
            Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        }
        DrawerFooter(strings)
    }

    TopDrawer(
        visible = drawer.isOpen(DRAWER_EXIT),
        onDismiss = { drawer.close() },
        restoreFocus = focusRequester,
        a11yTitle = strings.exitTitle,
    ) {
        InkText(
            text = strings.exitTitle,
            modifier = Modifier.fillMaxWidth(),
            style = Ink.Type.Title,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        InkText(
            text = strings.exitNote,
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
                highlighted = exitCursor.index == EXIT_CANCEL,
            )
            InkButton(
                text = strings.quit,
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

/** 抽屉底部的键位提示（↑↓ 选择 · Enter 确认 · Esc 返回）。 */
@Composable
private fun ColumnScope.DrawerFooter(strings: Strings) {
    Spacer(Modifier.height(DesignTokens.Spacing.Sm))
    InkText(
        text = strings.menuKeysEsc,
        modifier = Modifier.fillMaxWidth(),
        style = Ink.Type.Meta.copy(color = Ink.Light),
        textAlign = TextAlign.Center,
    )
}
