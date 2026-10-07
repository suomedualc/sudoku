package org.example.sudoku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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
import org.example.sudoku.ui.i18n.Strings
import org.example.sudoku.ui.i18n.keyTokenLabel
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/**
 * 键位设置抽屉的内容：五条绑定（上 / 下 / 左 / 右 / 擦除）+ 说明。
 *
 * **绑定方式是"点一下 → 按新键"**：候选键是开放的（字母 / 数字 / 空格 / 退格 / Del），
 * 下拉框做不了这件事。捕获态由**页面**持有（按键在页面根节点的 `onPreviewKeyEvent` 收）。
 */
@Composable
fun ColumnScope.KeyMapSheet(
    strings: Strings,
    keyMap: KeyMap,
    capturing: KeyAction?,
    /** 上一次捕获失败的原因（`null` = 没有）。 */
    captureHint: String? = null,
    onStartCapture: (KeyAction) -> Unit,
    onCancelCapture: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    InkText(
        text = strings.keymapTitle,
        modifier = Modifier.fillMaxWidth(),
        style = Ink.Type.Title.copy(color = Ink.Black),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(DesignTokens.Spacing.Xs))
    InkText(
        text = strings.keymapHintCapture,
        modifier = Modifier.fillMaxWidth(),
        style = Ink.Type.Meta.copy(color = Ink.Light),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(DesignTokens.Spacing.Md))

    for (action in KeyAction.entries) {
        KeyBindRow(
            strings = strings,
            action = action,
            token = keyMap.token(action),
            capturing = capturing == action,
            onStartCapture = onStartCapture,
            onCancelCapture = onCancelCapture,
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Xs))
    }

    // 捕获失败要说清原因：否则玩家按了个键却没绑上，只会以为界面坏了
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
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        InkButton(text = strings.keymapReset, onClick = onResetDefaults, compact = true)
    }
    Spacer(Modifier.height(DesignTokens.Spacing.Sm))
    // 绑定手势与兜底必须写在这里：键位设置是为了"更好用"，不能让人把自己锁在门外
    InkText(
        text = strings.keymapHintGestures,
        modifier = Modifier.fillMaxWidth(),
        style = Ink.Type.Meta.copy(color = Ink.Light),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(DesignTokens.Spacing.Xs))
    InkText(
        text = strings.keymapHintFallbacks,
        modifier = Modifier.fillMaxWidth(),
        style = Ink.Type.Meta.copy(color = Ink.Light),
        textAlign = TextAlign.Center,
    )
}

/** 一条：图标 + 动作名 + 当前键位（可点；点了就等下一个按键）。 */
@Composable
private fun KeyBindRow(
    strings: Strings,
    action: KeyAction,
    token: String,
    capturing: Boolean,
    onStartCapture: (KeyAction) -> Unit,
    onCancelCapture: () -> Unit,
) {
    val name = when (action) {
        KeyAction.Up -> strings.actionUp
        KeyAction.Down -> strings.actionDown
        KeyAction.Left -> strings.actionLeft
        KeyAction.Right -> strings.actionRight
        KeyAction.Erase -> strings.actionErase
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
            text = if (capturing) strings.keyCapturing else strings.keyTokenLabel(token),
            onClick = { if (capturing) onCancelCapture() else onStartCapture(action) },
            // 捕获态用重墨框：它是"这里正在等你"的明确信号，不能只是一个淡淡的框
            emphasized = capturing,
            compact = true,
            modifier = Modifier
                .width(DesignTokens.Sizes.KeyBindButton)
                .semantics {
                    contentDescription = if (capturing) {
                        strings.keyCapCapturing.format(name)
                    } else {
                        strings.keyCapBound.format(name, strings.keyTokenLabel(token))
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
fun KeyHintRow(strings: Strings, keyMap: KeyMap, modifier: Modifier = Modifier) {
    val label = { token: String -> strings.keyTokenLabel(token) }
    val text = buildString {
        append(strings.hintsMove)
        append("「" + label(keyMap.up) + "」「" + label(keyMap.down) + "」")
        append("「" + label(keyMap.left) + "」「" + label(keyMap.right) + "」")
        append(" · " + strings.hintsErase + "「" + label(keyMap.erase) + "」")
        append(" · " + strings.hintsFill + "「1–9」")
        append(" · " + strings.hintsNote + "「N」")
        append(" · " + strings.hintsHint + "「H」")
        append(" · " + strings.hintsPause + "「P」")
        append(" · " + strings.hintsUndo + "「Ctrl+Z」")
        append(" · " + strings.hintStep)
        append(" · " + strings.hintJump)
    }
    InkText(
        text = text,
        modifier = modifier.fillMaxWidth().padding(horizontal = DesignTokens.Spacing.Sm),
        style = Ink.Type.Meta.copy(color = Ink.Light),
        textAlign = TextAlign.Center,
    )
}
