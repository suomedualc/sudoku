package org.example.sudoku.state

/**
 * 一次性提示的**语义键**：状态机只说"发生了什么"，不说话。
 *
 * 为什么不让 reducer 直接返回文案：文案是 UI 的事（多语言、措辞、标点），
 * 而 reducer 在 `state/`（禁 UI 依赖），且单测断言的应是**语义**而不是某一种语言的句子。
 * 渲染在 `ui/i18n/Strings.render(msg)`，按当前语言取词。
 */
sealed interface Msg {
    /** 请先选择一个格子。 */
    data object SelectFirst : Msg

    /** 数字与所在行 / 列 / 宫冲突（[value] = 填的数字）。 */
    data class Conflict(val value: Int) : Msg

    /** 题目给定的数字不可修改。 */
    data object GivenImmutable : Msg

    /** 严格模式：该数字与规则冲突。 */
    data object StrictConflict : Msg

    /** 该格已填入数字，无法记笔记。 */
    data object NoNoteOnFilled : Msg

    /** 通关（[elapsed] 用时秒数，[usedHints] 是否用过提示）。 */
    data class Win(val elapsed: Int, val usedHints: Boolean) : Msg

    /** 本局已重置。 */
    data object ResetDone : Msg

    /** 已填入正确答案（提示）。 */
    data object HintFilled : Msg

    /** 已无可提示的空格。 */
    data object NoHintLeft : Msg

    /** 已显示答案，本局不计入胜场。 */
    data object Revealed : Msg

    /** 没有可撤销的步骤。 */
    data object NothingToUndo : Msg

    /** 没有可重做的步骤。 */
    data object NothingToRedo : Msg

    /** 夜墨模式：开。 */
    data object DarkModeOn : Msg

    /** 夜墨模式：关。 */
    data object DarkModeOff : Msg

    /** 候选提示：开。 */
    data object HintCandidatesOn : Msg

    /** 候选提示：关。 */
    data object HintCandidatesOff : Msg
}
