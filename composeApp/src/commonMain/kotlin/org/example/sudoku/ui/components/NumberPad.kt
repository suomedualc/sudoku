package org.example.sudoku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
) {
    if (!grid) {
        Row(
            modifier = modifier.fillMaxWidth(),
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
        return
    }

    // 九宫格：3×3 数字铺满宽度（每键 ≈ 宽度三分之一，≥70dp），擦除为右侧通高键——
    // 拇指热区一次到位，也把"擦除"从数字堆里分开，避免误触。
    val gridHeight = DesignTokens.Sizes.KeyHeight * 3 + DesignTokens.Spacing.Sm * 2
    Row(
        modifier = modifier.fillMaxWidth(),
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
