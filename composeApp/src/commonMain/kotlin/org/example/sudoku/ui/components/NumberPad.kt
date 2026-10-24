package org.example.sudoku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.example.sudoku.ui.i18n.Strings
import org.example.sudoku.ui.i18n.ZhStrings
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/**
 * 数字键盘（墨线版）：两种形态，按可用宽度择一（窄屏断点见
 * [DesignTokens.Sizes.FunctionNarrowMax]，由 GameScreen 判定后传参）：
 *
 * - **横排**（[grid] = false，默认）：1–9 一排 + 擦除固定宽——宽屏上的"条状输入条"读法，
 *   眼睛从上往下走一次就能找到数字（此前 3×3 像第二块棋盘，视觉来回跳）；
 * - **九宫格**（[grid] = true）：3×3 数字 + **竖置高擦除键**——窄屏（Android 手机 ~330-430dp）
 *   横排会被压成 ~19dp 的不可点细条（`docs/08` §4-D1），3×3 让每键回到 70dp+。
 *
 * 设计约定（两形态共用）：
 * - [legalMask] 为 `null` 表示**不做可填性区分**（未选格，或笔记模式）；
 * - 非 null 时：可填数字用**重墨框**、其余用**淡墨框**——用线重而不是颜色区分；
 * - 与规则冲突的数字**不禁用**：本作玩法是"允许填错 → 棋盘排线标记 + 墨字提示"；
 * - 暂停 / 结算时整体禁用（虚线框 + 淡墨字）。
 *
 * **双键盘（`showNoteRow`）**：主键盘下方再排一行**空心数字**，点它直接记笔记，
 * 无需先切"笔记模式"。这是触屏的核心便利——否则"填数 / 记笔记"每次都要先按一下开关，
 * 单手步数从 1 变 2（竞品「数独清」正是靠这个赢的，见 `docs/09`）。
 * 空心 = 笔记（"标注"），实心 = 落子，字形区分而不是靠颜色（全站铁律）。
 */
@Composable
fun NumberPad(
    legalMask: Int?,
    enabled: Boolean,
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
    modifier: Modifier = Modifier,
    /** 每个数字还剩几个未填（索引 0..8 对应数字 1..9）；传 null 则不显示角标。 */
    remaining: IntArray? = null,
    /** 字典（"擦除"等可见文字从这里取）。 */
    strings: Strings = ZhStrings,
    /** 九宫格形态：3×3 数字 + 竖置高擦除键（窄屏）。 */
    grid: Boolean = false,
    /**
     * 是否显示**笔记键盘**（主键盘下方的空心数字块）。
     *
     * 传 false 的场景：窗口极窄且纵向吃紧时（由调用方按实际可用高度决定），
     * 以及"当前选中的格已填"——那种情况下笔记本来就写不进去
     * （reducer 会回 `Msg.NoNoteOnFilled`），显示一排点了会报错的键没有意义。
     */
    showNoteRow: Boolean = false,
    /** 点笔记键盘的某个数字（与 [onDigit] 分开：语义不同，走 `GameAction.NoteDigit`）。 */
    onNoteDigit: (Int) -> Unit = {},
    /** 当前格已有的笔记掩码（bit i = 数字 i+1），用于把"已记下的"标成实心。 */
    noteMask: Int = 0,
) {
    // 笔记行与主键盘的间距：比常规 Sm 更紧，让它读起来是"同一块键盘的第二排"而不是两个控件
    val rowGap = DesignTokens.Spacing.Xs

    Column(modifier = modifier.fillMaxWidth()) {
        val drawNoteRow = showNoteRow

        Column(modifier = Modifier.fillMaxWidth()) {
            if (!grid) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm),
                ) {
                    for (digit in 1..9) {
                        DigitKey(digit, legalMask, remaining, enabled, onDigit, modifier = Modifier.weight(1f))
                    }
                    // 「擦除」单独占固定宽（见 DesignTokens.Sizes.EraseKeyWidth）：它是两个汉字，
                    // 跟着 1–9 一起等分会被压得比数字键还窄，读起来就不像"擦除"了。
                    InkKey(
                        text = strings.erase,
                        onClick = onErase,
                        modifier = Modifier.width(DesignTokens.Sizes.EraseKeyWidth),
                        enabled = enabled,
                        height = DesignTokens.Sizes.KeyHeight,
                        fontSize = Ink.Type.Body.fontSize,
                    )
                }
            } else {
                // 九宫格：3×3 数字铺满宽度（每键 ≈ 宽度三分之一，≥70dp），擦除为右侧通高键——
                // 拇指热区一次到位，也把"擦除"从数字堆里分开，避免误触。
                val gridHeight = DesignTokens.Sizes.KeyHeight * 3 + DesignTokens.Spacing.Sm * 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm),
                    ) {
                        for (row in 0..2) {
                            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm)) {
                                for (col in 0..2) {
                                    val digit = row * 3 + col + 1
                                    DigitKey(
                                        digit, legalMask, remaining, enabled, onDigit,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    InkKey(
                        text = strings.erase,
                        onClick = onErase,
                        modifier = Modifier.width(DesignTokens.Sizes.EraseKeyWidth),
                        enabled = enabled,
                        height = gridHeight,
                        fontSize = Ink.Type.Body.fontSize,
                    )
                }
            }

            if (drawNoteRow) {
                Spacer(Modifier.height(rowGap))
                // **统一单行**：1–9 排一行，与主键盘的 1–9 逐列对齐（右侧让出「擦除」宽度）。
                //
                // 曾经试过窄屏排 3×3（与主键盘同构），但 3×3 要 3×40+2×4 = 128dp 纵向，
                // 实测把「游戏设置」按钮挤出屏幕（360×640 渲染验证）；而单行只要 40dp，
                // 桌面 / 手机竖屏 / 横屏都放得下（见 GameScreen 的 showNotePad 预算）。
                //
                // **不留右侧空白**：留出「擦除」宽度会让每键从 36dp 掉到 27dp，
                // 而空心数字在 27dp 里会糊成一条竖线（渲染验证）。宁可**不与主键盘逐列对齐**，
                // 也要保证每个笔记键看得清——对齐是锦上添花，可读性是底线。
                //
                // 窄屏每键约 36dp：视觉偏窄，但命中区由 InkSurface 的 clickable 补到 48dp。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm),
                ) {
                    for (digit in 1..9) {
                        NoteKey(
                            digit = digit,
                            enabled = enabled,
                            marked = (noteMask and (1 shl (digit - 1))) != 0,
                            onClick = { onNoteDigit(digit) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 单个数字键（横排 / 九宫格共用）：可填性用线重、用完转淡墨框 + 角标 0 表达，不靠颜色。 */
@Composable
private fun DigitKey(
    digit: Int,
    legalMask: Int?,
    remaining: IntArray?,
    enabled: Boolean,
    onDigit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = remaining?.getOrNull(digit - 1)
    val usedUp = count != null && count <= 0
    val legal = legalMask == null || (legalMask and (1 shl (digit - 1))) != 0
    InkKey(
        text = digit.toString(),
        onClick = { onDigit(digit) },
        modifier = modifier,
        enabled = enabled,
        // "用完了"用淡墨框 + 角标 0 表达，而不是靠颜色
        emphasis = !usedUp && legalMask != null && legal,
        soft = usedUp || (legalMask != null && !legal),
        badge = count?.toString(),
        digit = true,
        height = DesignTokens.Sizes.KeyHeight,
        fontSize = Ink.Type.Title.fontSize,
    )
}

/**
 * 笔记键：**空心数字**（描边字），点它把该数字记进当前格的笔记。
 *
 * 为什么用空心而不是"小一号的实心数字"：
 * - 空心 = "标注 / 备注"的通用视觉隐喻，和主键盘的实心数字一眼分开；
 * - 全站铁律是**不靠颜色**区分状态（见 docs/02 §3），字形是最稳的手段——
 *   夜墨主题下颜色翻转，字形不翻；
 * - 已记下的数字画**实心**（当前格的笔记掩码），于是"哪些记了"在一排里直接可读，
 *   不需要再去棋盘上找灰数字。
 *
 * 交互与五态反馈全部复用 [InkKey]（悬停 / 按压 / 焦点），这里只负责"空心 vs 实心"这一个字形开关。
 */
@Composable
private fun NoteKey(
    digit: Int,
    enabled: Boolean,
    marked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InkKey(
        text = digit.toString(),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        // 已记下 → 重墨实心（"这条笔记在"）；未记 → 淡墨空心（"可以记"）
        emphasis = marked,
        soft = !marked,
        height = DesignTokens.Sizes.NoteKeyHeight,
        fontSize = Ink.Type.Body.fontSize,
        digit = true,
        hollow = !marked,
    )
}
