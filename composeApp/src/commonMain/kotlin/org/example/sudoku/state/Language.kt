package org.example.sudoku.state

/**
 * 界面语言（**第七项跨对局偏好**，随存档落盘）。
 *
 * [label] 是"用**自己的语言**写自己的名字"（语言选择器的通用做法）：
 * 无论当前界面是什么语言，这一项都看得懂。
 *
 * 默认 [ZhCn] 而不是跟随系统：让首次运行的语言**确定**（本作以中文为先），
 * 也让测试断言稳定；想跟随系统的玩家可以在设置里显式选 [FollowSystem]。
 */
enum class AppLanguage(val label: String) {
    FollowSystem("跟随系统 · System"),
    ZhCn("简体中文"),
    En("English");

    companion object {
        /** 存档是外部输入：认不出的值取默认，不让一个坏字段废掉整套设置。 */
        fun decode(text: String?): AppLanguage =
            entries.firstOrNull { it.name == text?.trim() } ?: ZhCn
    }
}
