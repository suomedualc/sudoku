package org.example.sudoku.ui.i18n

import org.example.sudoku.platform.AppGlobals
import org.example.sudoku.state.AppLanguage

/**
 * 系统语言：`zh` 开头 → 简体中文，其余一律英文（与桌面同一套粗判规则）。
 * 用系统级 Resources——它的 locale 反映设备当前配置，不需要 Activity Context。
 */
actual fun systemAppLanguage(): AppLanguage {
    val locale = java.util.Locale.getDefault()
    return if (locale.language.startsWith("zh", ignoreCase = true)) AppLanguage.ZhCn else AppLanguage.En
}

/**
 * Android 端语料加载：读 `assets/corpus/sentences.txt`（与桌面同一份语料库文件，
 * 各端按自己的资产机制打包）。解析与兜底在 commonMain（parseSentenceCorpus）。
 * AppGlobals 未初始化或读不到时返回 null → 走单条兜底。
 */
internal actual fun readSentenceCorpusText(): String? {
    val assets = AppGlobals.appContext?.assets ?: return null
    return runCatching {
        assets.open("corpus/sentences.txt").use { it.readBytes().decodeToString() }
    }.getOrNull()
}
