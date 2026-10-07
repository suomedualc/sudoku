package org.example.sudoku.ui.i18n

import org.example.sudoku.state.AppLanguage
import java.util.Locale

/**
 * 桌面端读系统语言：`zh` 开头 → 简体中文，其余一律英文。
 *
 * 只做一次粗判（不区分 `zh_TW` 等变体）——本作目前只有中英两套，
 * 以后加语言时在这里扩映射即可。
 */
actual fun systemAppLanguage(): AppLanguage =
    if (Locale.getDefault().language.startsWith("zh", ignoreCase = true)) AppLanguage.ZhCn else AppLanguage.En
