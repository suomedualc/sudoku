package org.example.sudoku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import org.example.sudoku.state.KeyAction
import org.example.sudoku.state.KeyMap
import org.example.sudoku.state.keyTokenLabel
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/**
 * 键位设置抽屉的内容：五条绑定（上 / 下 / 左 / 右 / 擦除）+ 一行说明。
 *
 * **绑定方式是"点一下 → 按新键"，而不是下拉框**：
 * 下拉框要新做一个自绘控件（本项目不引第三方组件库），而"按下即绑"既符合游戏里的惯例，
 * 也让"我要用的那个键"不必在列表里找——候选键是开放的（字母 / 数字 / 空格 / 退格 / Del）。
 *
 * @param capturing 正在等待按键的动作（`null` = 没在捕获）。捕获态由**页面**持有：
 *   按键是在页面根节点的 `onPreviewKeyEvent` 里收的，抽屉自己收不到。
 */
@Composable
fun KeyMapSheet(
    keyMap: KeyMap,
    capturing: KeyAction?,
    /** 上一次捕获失败的原因（`null` = 没有）。 */
    captureHint: String? = null,
    onStartCapture: (KeyAction) -> Unit,
    onCancelCapture: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    InkText(
        text = "键位设置",
        modifier = Modifier.fillMaxWidth(),
        style = Ink.Type.Title.copy(color = Ink.Black),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(DesignTokens.Spacing.Xs))
    InkText(
        text = "点一下右侧的键位，再按下你想用的键",
        modifier = Modifier.fillMaxWidth(),
        style = Ink.Type.Meta.copy(color = Ink.Light),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(DesignTokens.Spacing.Md))

    for (action in KeyAction.entries) {
        KeyBindRow(
            action = action,
            token = keyMap.token(action),
            capturing = capturing == action,
            onStartCapture = onStartCapture,
            onCancelCapture = onCancelCapture,
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Xs))
    }

    // 捕获失败要**说清原因**：否则玩家按了个键却没绑上，只会以为界面坏了
    if (captureHint != null) {
        Spacer(Modifier.height(DesignTokens.Spacing.Xs))
        InkText(
            text = captureHint,
            modifier = Modifier.fillMaxWidth(),
            style = Ink.Type.Meta.copy(color = Ink.Grey),
            textAlign = TextAlign.Center,
        )
    }

    Spacer(Modifier.height(DesignTokens.Spacing.Sm))
    // 用 Box 居中而不是 `Modifier.align`：本组件不声明 ColumnScope receiver，
    // 免得调用方必须处在 Column 里才能用
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        InkButton(text = "恢复默认键位", onClick = onResetDefaults, compact = true)
    }
    Spacer(Modifier.height(DesignTokens.Spacing.Sm))
    // 三条兜底必须写在这里：键位设置是为了"更好用"，不能让人把自己锁在门外
    InkText(
        text = "方向键始终可移动，退格 / Del 始终可擦除；" +
            "按住 Shift 再按方向键可逐格移动（经过自己填过的格子，便于回去改）",
        modifier = Modifier.fillMaxWidth(),
        style = Ink.Type.Meta.copy(color = Ink.Light),
        textAlign = TextAlign.Center,
    )
}

/** 一条：图标 + 动作名 + 当前键位（可点；点了就等下一个按键）。 */
@Composable
private fun KeyBindRow(
    action: KeyAction,
    token: String,
    capturing: Boolean,
    onStartCapture: (KeyAction) -> Unit,
    onCancelCapture: () -> Unit,
) {
    val name = when (action) {
        KeyAction.Up -> "向上"
        KeyAction.Down -> "向下"
        KeyAction.Left -> "向左"
        KeyAction.Right -> "向右"
        KeyAction.Erase -> "擦除"
    }
    val icon = when (action) {
        KeyAction.Up -> InkIcon.ArrowUp
        KeyAction.Down -> InkIcon.ArrowDown
        KeyAction.Left -> InkIcon.ArrowLeft
        KeyAction.Right -> InkIcon.ArrowRight
        KeyAction.Erase -> InkIcon.Eraser
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm),
    ) {
        InkIconGlyph(icon = icon, tint = Ink.Grey)
        InkText(
            text = name,
            modifier = Modifier.weight(1f),
            style = Ink.Type.Body.copy(color = Ink.Black),
        )
        InkButton(
            text = if (capturing) "按下按键…" else keyTokenLabel(token),
            onClick = { if (capturing) onCancelCapture() else onStartCapture(action) },
            // 捕获态用重墨框：它是"这里正在等你"的明确信号，不能只是一个淡淡的框
            emphasized = capturing,
            compact = true,
            modifier = Modifier
                .width(DesignTokens.Sizes.KeyBindButton)
                .semantics {
                    contentDescription = if (capturing) {
                        "$name 键位：正在等待按键，按下 Esc 取消"
                    } else {
                        "$name 键位：${keyTokenLabel(token)}，点击后按下新键"
                    }
                },
        )
    }
}

/**
 * 底部**键位提示行**：按当前设置**动态**生成（改了键位，这一行立刻跟着变）。
 *
 * 为什么做成一行淡墨小字而不是一组键帽：它是"看一眼"的辅助信息，
 * 画成按钮样的键帽会和功能区抢注意力（上一轮刚把棋盘四周清空，不该在底部又立一排控件）。
 */
@Composable
fun KeyHintRow(keyMap: KeyMap, modifier: Modifier = Modifier) {
    val label = { token: String -> keyTokenLabel(token) }
    val text = buildString {
        append("移动「${label(keyMap.up)}」「${label(keyMap.down)}」")
        append("「${label(keyMap.left)}」「${label(keyMap.right)}」")
        append(" · 擦除「${label(keyMap.erase)}」")
        append(" · 填数「1–9」")
        append(" · 笔记「N」")
        append(" · 提示「H」")
        append(" · 暂停「P」")
        append(" · 撤销「Ctrl+Z」")
        append(" · Shift+方向 逐格（可回到已填的格子）")
    }
    InkText(
        text = text,
        modifier = modifier.fillMaxWidth().padding(horizontal = DesignTokens.Spacing.Sm),
        style = Ink.Type.Meta.copy(color = Ink.Light),
        textAlign = TextAlign.Center,
    )
}
