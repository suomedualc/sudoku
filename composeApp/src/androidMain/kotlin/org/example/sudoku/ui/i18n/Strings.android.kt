package org.example.sudoku.ui.i18n

import org.example.sudoku.platform.AppGlobals
import org.example.sudoku.state.AppLanguage

/**
 * 系统语言：`zh` 开头 → 简体中文，其余一律英文（与桌面同一套粗判规则）。
 *
 * 用 `Locale.getDefault()` 而**不是** `Resources.getSystem().configuration.locales`：
 * - 本作只有中英两套，且 `AppLanguage.FollowSystem` 只在一处解析（`stringsFor`），
 *   粗判足够；桌面端（`Strings.jvm.kt`）也是同一行实现，两端行为因此天然一致。
 * - `Resources.getSystem()` 拿到的是**系统**配置而非**应用**配置，在多语言用户手动
 *   "按应用设置语言"的场景下反而会读错，还得额外引 `AppCompatDelegate` / `LocaleManager`。
 *
 * 以后要区分 `zh_TW` 等变体、或要跟随"按应用设置的语言"，改这里一处即可。
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
