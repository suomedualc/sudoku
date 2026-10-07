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

/** 本地语料库文件在 resources 中的路径（打进 jar，与字体同一套资源机制）。 */
private const val CORPUS_RESOURCE = "corpus/sentences.txt"

/**
 * 桌面端语料加载：读打包的**本地语料库文件**（结构化每行 `类别|正文|出处`，格式见文件头）。
 * 解析与兜底在 commonMain（[parseSentenceCorpus]），这里只负责拿到字符串。
 */
internal actual fun readSentenceCorpusText(): String? = runCatching {
    SentenceBook::class.java.classLoader.getResourceAsStream(CORPUS_RESOURCE)
        ?.use { it.readBytes().decodeToString() }
}.getOrNull()
