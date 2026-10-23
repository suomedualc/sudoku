package org.example.sudoku.ui.i18n

import org.example.sudoku.state.Msg
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * i18n 守卫：机械化校验中英两份字典的**键 / 占位符一致性**与**运行时渲染闭环**。
 *
 * 背景：字典是手写的 [Strings] 接口 + [ZhStrings] / [EnStrings] 两个 object。
 * 键的"存在性"由编译器保证（漏 `override` 直接编译失败），但编译器查不出三类回归：
 * ① 翻译漏成空白；② 中英 `format` 占位符不一致；③ 模板占位符与 `render()` 实际传入的
 *    参数数量/类型不匹配（如 `msgWin` 被改成两个 `%s`，`render` 却只 `format` 一个时长，
 *    只有真正执行 `format` 才会抛 `MissingFormatArgumentException`）。
 *
 * 本守卫分两段：
 * 1. [zhAndEnDictionariesStayConsistent]——反射枚举全部字符串键，断言中英非空白 + 占位符一致；
 * 2. [everyMessageRendersInBothLanguages]——枚举**全部 [Msg] 语义键**，在两种语言下真实执行
 *    `render()`，断言不抛异常且非空白（把 ③ 抓出来）。
 */
class I18nGuardTest {

    @Test
    fun zhAndEnDictionariesStayConsistent() {
        val getters = Strings::class.java.declaredMethods
            .filter { it.parameterCount == 0 && it.returnType == String::class.java }

        assertTrue(getters.isNotEmpty(), "应能通过接口 getter 枚举到字典的字符串键")

        val violations = buildList {
            for (m in getters) {
                val key = m.name.removePrefix("get")
                val zh = m.invoke(ZhStrings) as String
                val en = m.invoke(EnStrings) as String
                when {
                    zh.isBlank() || en.isBlank() ->
                        add("$key：存在空白翻译（zh=${zh.show()} / en=${en.show()}）")
                    zh.placeholders() != en.placeholders() ->
                        add("$key：占位符不一致（zh=${zh.placeholders()} / en=${en.placeholders()}）")
                }
            }
        }

        assertTrue(violations.isEmpty(), "i18n 键不一致（${violations.size} 处）：\n" + violations.joinToString("\n"))
    }

    @Test
    fun everyMessageRendersInBothLanguages() {
        val failures = buildList {
            for (msg in allMessages) {
                for ((lang, dict) in listOf("zh" to ZhStrings, "en" to EnStrings)) {
                    val text = runCatching { dict.render(msg) }.getOrElse { e ->
                        add("$lang.render($msg) 抛异常：${e::class.simpleName}: ${e.message}")
                        return@getOrElse ""
                    }
                    if (text.isBlank()) add("$lang.render($msg) 渲染为空")
                }
            }
        }

        assertTrue(failures.isEmpty(), "渲染失败/空文本（${failures.size} 处）：\n" + failures.joinToString("\n"))
    }

    /** 提取 `String.format` 风格的占位符（`%s` / `%d` / `%1$s` / `%2$d` …）。 */
    private fun String.placeholders(): Set<String> =
        Regex("%(?:\\d+\\$)?[sd]").findAll(this).map { it.value }.toSet()

    private fun String.show(): String = if (isEmpty()) "<空>" else this

    /** 全部 [Msg] 语义键（含 `Win` 的两种分支、以及带参的 `Conflict`）。 */
    private val allMessages: List<Msg> = listOf(
        Msg.SelectFirst,
        Msg.Conflict(7),
        Msg.GivenImmutable,
        Msg.StrictConflict,
        Msg.NoNoteOnFilled,
        Msg.Win(elapsed = 65, usedHints = false),
        Msg.Win(elapsed = 65, usedHints = true),
        Msg.ResetDone,
        Msg.HintFilled,
        Msg.NoHintLeft,
        Msg.Revealed,
        Msg.NothingToUndo,
        Msg.NothingToRedo,
        Msg.DarkModeOn,
        Msg.DarkModeOff,
        Msg.HintCandidatesOn,
        Msg.HintCandidatesOff,
    )
}