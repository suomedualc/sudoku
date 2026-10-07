package org.example.sudoku.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import org.example.sudoku.ui.i18n.SentenceBook
import org.example.sudoku.ui.i18n.SentenceCategory
import org.example.sudoku.ui.i18n.Strings
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink
import kotlin.random.Random

/** 首页句子自动轮播的间隔（点「换一句」随时可换）。 */
private const val SENTENCE_ROTATE_MS = 20_000L

/**
 * 首页底部的随机句子：内置语料 + 定时 / 手动轮播。
 *
 * - 获取：启动时从语料库随机挑一句装入内存（每次启动都不同）；
 * - 缓存：会话内记住当前句，轮播时不会立刻重复；
 * - 轮播：每 20 秒自动换一句，点「换一句」随时换。
 * - 脑筋急转弯的答案直接以「答案：…」给出（换一句看下一题，不做翻面交互）。
 */
@Composable
fun SentenceStrip(strings: Strings, modifier: Modifier = Modifier) {
    var index by remember { mutableStateOf(Random.nextInt(SentenceBook.all.size)) }

    // 定时轮播：只在首页可见时运行（本组合销毁即停）
    LaunchedEffect(Unit) {
        while (true) {
            delay(SENTENCE_ROTATE_MS)
            index = nextSentenceIndex(index)
        }
    }

    val sentence = SentenceBook.all[index]
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        InkText(
            text = sentence.text,
            style = Ink.Type.Caption.copy(color = Ink.Grey),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Xs))
        val source = if (sentence.category == SentenceCategory.Riddle) {
            "答案：" + sentence.source
        } else {
            sentence.source
        }
        val attribution = "—— " + SentenceBook.categoryLabel(sentence.category) + " · " + source
        InkText(
            text = attribution,
            style = Ink.Type.Meta.copy(color = Ink.Light),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        InkButton(
            text = strings.sentenceAnother,
            onClick = { index = nextSentenceIndex(index) },
            compact = true,
        )
    }
}

/** 随机挑下一句，且不与当前句重复（语料只有一句时原地不动）。 */
private fun nextSentenceIndex(current: Int): Int {
    val size = SentenceBook.all.size
    if (size <= 1) return current
    var next = Random.nextInt(size)
    if (next == current) next = (next + 1 + Random.nextInt(size - 1)) % size
    return next
}
