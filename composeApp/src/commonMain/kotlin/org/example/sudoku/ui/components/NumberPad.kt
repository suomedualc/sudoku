package org.example.sudoku.ui.components

import androidx.compose.foundation.layout.Arrangement
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
 * 数字键盘（墨线版）：**1–9 横向一排** + 擦除。
 *
 * 为什么改成横排：此前是 3×3 九宫格，看着"像棋盘"，于是棋盘旁边多了一块同样密的东西，
 * 视线一直在两处跳——而数字键盘的作用是**输入框**，不是第二块题面。
 * 一排 1–9 才是"条状输入条"的读法，眼睛从上往下走一次就能找到数字。
 *
 * 设计约定：
 * - [legalMask] 为 `null` 表示**不做可填性区分**（未选格，或笔记模式——笔记本就不受同行列宫限制），
 *   此时全部用常规墨框；
 * - 非 null 时：可填数字用**重墨框**、其余用**淡墨框**——用线重而不是颜色区分；
 * - 与规则冲突的数字**不禁用**：本作玩法是"允许填错 → 棋盘排线标记 + 墨字提示"，
 *   入口直接禁掉会让错误反馈失去意义；
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
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm),
    ) {
        for (digit in 1..9) {
            val count = remaining?.getOrNull(digit - 1)
            val usedUp = count != null && count <= 0
            val legal = legalMask == null || (legalMask and (1 shl (digit - 1))) != 0
            InkKey(
                text = digit.toString(),
                onClick = { onDigit(digit) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                // "用完了"用淡墨框 + 角标 0 表达，而不是靠颜色
                emphasis = !usedUp && legalMask != null && legal,
                soft = usedUp || (legalMask != null && !legal),
                badge = count?.toString(),
                digit = true,
            )
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
}
