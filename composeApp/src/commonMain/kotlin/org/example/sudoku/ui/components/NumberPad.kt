package org.example.sudoku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/**
 * 数字键盘（墨线版）：3×3 数字 + 擦除。
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
) {
    Column(modifier = modifier) {
        repeat(3) { rowIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm),
            ) {
                repeat(3) { colIndex ->
                    val digit = rowIndex * 3 + colIndex + 1
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
            }
            if (rowIndex < 2) Spacer(modifier = Modifier.height(DesignTokens.Spacing.Sm))
        }
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.Sm))
        InkKey(
            text = "擦除",
            onClick = onErase,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            height = DesignTokens.Sizes.CompactItemHeight,
            fontSize = Ink.Type.Body.fontSize,
        )
    }
}
