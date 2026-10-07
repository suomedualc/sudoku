package org.example.sudoku.ui.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 本地语料库文件（`jvmMain/resources/corpus/sentences.txt`）的契约：资源在、可解析、字段完整、
 * 三个类别都有语料且不重复。有人改坏格式或漏带资源时这里会失败——否则兜底生效，
 * 首页只会默默剩下一句，肉眼很难发现。
 *
 * commonTest 对 jvm 目标编译时解析到 jvmMain 的 actual，因此直接测加载结果。
 */
class SentenceCorpusTest {

    @Test
    fun bundledCorpusLoadsAndIsWellFormed() {
        val corpus = loadSentenceCorpus()
        assertTrue(corpus.size >= 30, "语料库应有 30+ 条（实际 ${corpus.size}）；只剩兜底的一条说明资源没打进包")
        assertEquals(3, corpus.map { it.category }.toSet().size, "三个类别都应有语料")
        assertTrue(
            corpus.all { it.text.isNotBlank() && it.source.isNotBlank() },
            "正文与出处 / 答案都不得为空",
        )
        assertEquals(corpus.map { it.text }.toSet().size, corpus.size, "语料不得重复")
    }
}
