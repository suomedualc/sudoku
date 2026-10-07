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
 *
 * 三层兜底，绝不让首页开天窗：
 * - 资源读不到（理论上不可能——文件随包分发）→ 单条兜底；
 * - 个别行格式坏 → 跳过该行，其余照常；
 * - 解析结果为空 → 单条兜底。
 */
internal actual fun loadSentenceCorpus(): List<Sentence> {
    val text = runCatching {
        SentenceBook::class.java.classLoader.getResourceAsStream(CORPUS_RESOURCE)
            ?.use { it.readBytes().decodeToString() }
    }.getOrNull()
    if (text == null) return singleSentenceFallback()

    val parsed = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            runCatching {
                val parts = line.split('|')
                require(parts.size == 3) { "字段数不为 3" }
                val (category, content, source) = parts.map { it.trim() }
                require(content.isNotEmpty() && source.isNotEmpty()) { "正文字段为空" }
                Sentence(SentenceCategory.valueOf(category), content, source)
            }.getOrNull()
        }.toList()
    return parsed.ifEmpty { singleSentenceFallback() }
}

/** 兜底语料：资源完全读不到时仍给首页一句可用的话。 */
private fun singleSentenceFallback(): List<Sentence> =
    listOf(Sentence(SentenceCategory.Quote, "千里之行，始于足下。", "老子 ·《道德经》"))
