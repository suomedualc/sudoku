package org.example.sudoku.ui.i18n

/**
 * 首页随机句子的**本地语料库**。
 *
 * 收集与存储机制（明确分工，运行期**不访问网络**）：
 * - **收集**：开发期从公开网络资料收集（古典诗文为公有领域，名人名言附出处，
 *   脑筋急转弯为流传较广的民间段子），整理成**结构化数据文件**
 *   `jvmMain/resources/corpus/sentences.txt`（每行 `类别|正文|出处`，见文件头说明）；
 * - **存储**：该文件即"本地语料库"，随应用打包分发，是语料的**唯一数据源**；
 * - **读取**：程序启动时由平台 actual（[loadSentenceCorpus]）一次性读入内存，
 *   轮播（定时 + 手动）在内存中进行——**离线状态下语料服务完全可用**；
 * - **更新**：换 / 加语料只改数据文件，不动任何逻辑代码。
 */
enum class SentenceCategory {
    /** 名人名言。 */
    Quote,

    /** 诗词歌赋。 */
    Verse,

    /** 脑筋急转弯（[Sentence.source] 是答案）。 */
    Riddle,
}

data class Sentence(
    val category: SentenceCategory,
    /** 正文。 */
    val text: String,
    /** 出处 / 作者 / 答案（脑筋急转弯 = 答案）。 */
    val source: String,
)

/** 语料库：轮播时在**全库**里随机（避免只在一个类别里打转）。 */
object SentenceBook {

    /** 全部句子（启动时从本地语料库文件一次性读入；解析失败由 actual 兜底，不会为空）。 */
    val all: List<Sentence> = loadSentenceCorpus()

    /** 类别的展示名（语料是中文内容，标签也用中文；属"内容"而非界面文字，不进多语言字典）。 */
    fun categoryLabel(category: SentenceCategory): String = when (category) {
        SentenceCategory.Quote -> "名人名言"
        SentenceCategory.Verse -> "诗词歌赋"
        SentenceCategory.Riddle -> "脑筋急转弯"
    }
}

/**
 * 从打包的**本地语料库文件**读入全部句子（`expect/actual`：
 * 桌面读 `resources/corpus/sentences.txt`；Android 读 `assets/corpus/sentences.txt`）。
 */
internal expect fun readSentenceCorpusText(): String?

/** 加载 = 平台读文本 + **平台无关**的解析（解析在这里，各端 actual 只负责拿到字符串）。 */
internal fun loadSentenceCorpus(): List<Sentence> = parseSentenceCorpus(readSentenceCorpusText())

/**
 * 解析语料文本：每行 `类别|正文|出处`，行首 `#` 注释、空行忽略；
 * 个别坏行跳过，解析结果为空或文本拿不到时返回单条兜底——绝不让首页开天窗或崩在 `Random.nextInt(0)`。
 */
internal fun parseSentenceCorpus(text: String?): List<Sentence> {
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

/** 兜底语料：语料完全读不到时仍给首页一句可用的话。 */
internal fun singleSentenceFallback(): List<Sentence> =
    listOf(Sentence(SentenceCategory.Quote, "千里之行，始于足下。", "老子 ·《道德经》"))
