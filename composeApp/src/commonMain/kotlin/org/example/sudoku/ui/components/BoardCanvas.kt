package org.example.sudoku.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import org.example.sudoku.core.Game
import org.example.sudoku.state.AppLanguage
import org.example.sudoku.core.Sudoku
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.i18n.Strings
import org.example.sudoku.ui.i18n.stringsFor
import org.example.sudoku.ui.theme.Ink
import org.example.sudoku.ui.theme.LocalReduceMotion
import org.example.sudoku.ui.theme.inkHatch
import org.example.sudoku.ui.theme.inkLine
import org.example.sudoku.ui.theme.inkRoundRect
import org.example.sudoku.ui.theme.motionDurationMs

/**
 * 棋盘绘制（手写纸 · 油墨版）：只做「从状态画成图」，不含规则。
 *
 * 墨线表达（全部不依赖颜色）：
 * - 单元格层级：冲突（斜排线）> 选中（淡墨 + 手绘双框）> 同值（淡墨）> 同行列宫（极淡墨）
 * - 数字：题目给定 = 实墨；玩家填入 = 淡墨；冲突数字再套一个手绘圈
 * - 笔记：更小的淡墨实心数字，3×3 排布（玩家自己记的）
 * - 候选提示（[hintCandidates] 开启时）：**所有空格**都用更小的**半透明灰**数字直接画出规则允许的候选；
 *   已有笔记的格子显示笔记（那是玩家自己的判断）。同一套 3×3 排布，靠**墨色深浅**区分来源
 * - 选中框：常规模式实线、笔记模式虚线（用线型而不是颜色区分模式）
 * - 尺寸：调用方决定（宽屏取可用高宽的正方形，窄屏占满宽度），本组件保证 1:1
 *
 * @param onCellClick 点击格子：回传格子下标与**是否精确指针**（鼠标 / 触控笔为真，手指为假）。
 *   调用方据此决定要不要弹"悬浮数字面板"——手指会挡住面板，触屏走常驻数字键盘（见 `docs/02` §6）。
 * @param paused 暂停时**连带遮住读屏**：81 个格子的语义一起清空，只保留整盘的"已暂停"文案。
 *   否则视觉上盖了白纸，读屏仍能逐格念出答案——遮题要遮得彻底。
 */
@Composable
fun BoardCanvas(
    game: Game,
    selected: Int?,
    notes: IntArray,
    conflicts: BooleanArray,
    noteMode: Boolean,
    showNotes: Boolean = true,
    hintCandidates: Boolean = false,
    paused: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    /** 界面语言：格子级读屏文案按语言取词（朗读的"给定 / 填入"也要跟语言走）。 */
    language: AppLanguage = AppLanguage.ZhCn,
    onCellClick: (cell: Int, precisePointer: Boolean) -> Unit,
) {
    Box(modifier = modifier.aspectRatio(1f)) {
        BoardCanvasDrawing(
            game = game,
            selected = selected,
            notes = notes,
            conflicts = conflicts,
            noteMode = noteMode,
            showNotes = showNotes,
            hintCandidates = hintCandidates,
            modifier = Modifier.matchParentSize(),
            onCellClick = onCellClick,
        )
        // 格子级无障碍：81 个语义节点，读屏可逐格朗读「行列 + 状态 + 值 / 笔记」
        BoardCellSemantics(
            game = game,
            notes = notes,
            conflicts = conflicts,
            selected = selected,
            paused = paused,
            strings = stringsFor(language),
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * 「落笔成局」：**开局时棋盘由淡到浓显影一次**——全站唯一一处"情感化"动效。
 *
 * @param key 一局的身份：传 `game.puzzle` 的**引用**。一局之内 reducer 只做 `game.copy(current = ...)`，
 *   puzzle 数组始终是同一个实例；开新局（或载入存档）才会换新实例 → 只有那时重播一次。
 *   同一局内的任何重组（计时心跳、填数、改选中、开关面板）都不会重播。
 *
 * 两条硬约束（来自 `docs/07` §4 的验收标准）：
 * - **不阻塞首帧交互**：显影只作用在绘制层的 alpha 上，点击与键盘在动画期间照常生效（有 UI 测试守着）；
 * - **减少动效时必须真正归零**：开启时初始就是"已显影"，不会出现"先空一帧再出现"的闪烁
 *   （半速 / 变淡都不接受，见 `motionDurationMs` 的说明）。
 */
@Composable
private fun rememberBoardReveal(key: Any?): Float {
    val reduceMotion = LocalReduceMotion.current
    var revealed by remember(key) { mutableStateOf(reduceMotion) }
    LaunchedEffect(key) { revealed = true }
    return animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = motionDurationMs(DesignTokens.Motion.RevealMs)),
        label = "boardReveal",
    ).value
}

/**
 * 「光标落纸」：换格瞬间在格子上洇一层淡墨，随即退去（1 → 0）。
 *
 * 两个刻意的选择：
 * - **只叠淡墨，不加新形状**：格子里已经有选中框、题面字框、候选小字与冲突排线，
 *   再往里放一圈线会互相打架；而且选中框本身始终是实心的——不会因为连按方向键而忽明忽暗
 *   （"反馈"做成让主框闪烁，是最容易翻车的做法）。
 * - **减少动效时完全不做**：`LocalReduceMotion` 打开时直接恒为 0。
 */
@Composable
private fun rememberCursorLanding(selected: Int?): Float {
    val reduceMotion = LocalReduceMotion.current
    var land by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(selected) {
        if (selected == null || reduceMotion) {
            land = 0f
            return@LaunchedEffect
        }
        // 用**纯函数**那一档 `motionDurationMs(ms, reduceMotion)`：这里是 LaunchedEffect 的协程，
        // 不是组合上下文，取不到 `LocalReduceMotion`（组合版那个重载是 @Composable）。
        animate(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = motionDurationMs(DesignTokens.Motion.CursorLandMs, reduceMotion),
            ),
        ) { value, _ -> land = value }
    }
    return land
}

@Composable
private fun BoardCanvasDrawing(
    game: Game,
    selected: Int?,
    notes: IntArray,
    conflicts: BooleanArray,
    noteMode: Boolean,
    showNotes: Boolean = true,
    hintCandidates: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onCellClick: (cell: Int, precisePointer: Boolean) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val onClick by rememberUpdatedState(onCellClick)

    // 「落笔成局」：开局时整盘由淡到浓显影（只作用于绘制层的 alpha，不吃点击）
    val reveal = rememberBoardReveal(game.puzzle)

    // 跨帧缓存文本测量：键含数字、颜色、字号(px)、字重
    val valueCache = remember { mutableMapOf<InkTextKey, TextLayoutResult>() }
    val noteCache = remember { mutableMapOf<InkTextKey, TextLayoutResult>() }

    // 候选提示：整盘一次算好，只在**盘面变化**时重算（换格 / 选中不动盘面时复用）
    val candidateMasks = remember(game.current) {
        IntArray(81) { pos -> if (game.current[pos] == 0) Sudoku.legalMask(game.current, pos) else 0 }
    }

    // 光标落纸：换格时在格子上洇一层淡墨，随即退去（见 rememberCursorLanding）
    val cursorLand = rememberCursorLanding(selected)

    Canvas(
        modifier = modifier
            .graphicsLayer { alpha = reveal }
            .pointerInput(Unit) {
                // 自己实现"点击"而不是用 detectTapGestures：需要知道**指针类型**——
                // 鼠标 / 触控笔是精确指针（可以弹悬浮输入面板），手指不是（面板会被手指挡住）。
                // awaitEachGesture + waitForUpOrCancellation 与 detectTapGestures 同源：
                // 移动超出触摸阈值、或被其它手势消费时会返回 null（即不算点击）。
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val precise = down.type != PointerType.Touch
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    val side = minOf(size.width, size.height).toFloat()
                    val cellPx = side / 9f
                    val col = (up.position.x / cellPx).toInt().coerceIn(0, 8)
                    val row = (up.position.y / cellPx).toInt().coerceIn(0, 8)
                    onClick(row * 9 + col, precise)
                }
            },
    ) {
        val cellPx = minOf(size.width, size.height) / 9f
        val selectedValue = selected?.let { game.current[it] } ?: 0

        // 0) 纸面（棋盘用纯白，和页面暖白纸形成极淡层次）
        drawRect(color = Ink.PaperSheet, size = Size(cellPx * 9f, cellPx * 9f))

        fun cellRect(pos: Int) = Rect(
            left = (pos % 9) * cellPx,
            top = (pos / 9) * cellPx,
            right = (pos % 9 + 1) * cellPx,
            bottom = (pos / 9 + 1) * cellPx,
        )

        /** 3×3 小字的落点（笔记与候选提示共用同一套排布）。 */
        fun miniTopLeft(r: Rect, digit: Int): Offset {
            val idx = digit - 1
            return Offset(
                r.left + cellPx * (0.17f + (idx % 3) * 0.29f),
                r.top + cellPx * (0.15f + (idx / 3) * 0.29f),
            )
        }

        // 1) 单元格墨色层次
        for (pos in 0..80) {
            val r = cellRect(pos)
            if (conflicts[pos]) {
                drawRect(
                    color = Ink.Black.copy(alpha = Ink.Alpha.Wash),
                    topLeft = Offset(r.left, r.top),
                    size = Size(r.width, r.height),
                )
                inkHatch(
                    r,
                    spacingPx = cellPx * 0.22f,
                    widthPx = DesignTokens.Stroke.Hatch.toPx(),
                    seed = pos * 7,
                )
                continue
            }
            val isPeer = selected != null && selected != pos &&
                (Sudoku.rowOf(pos) == Sudoku.rowOf(selected) ||
                    Sudoku.colOf(pos) == Sudoku.colOf(selected) ||
                    Sudoku.boxOf(pos) == Sudoku.boxOf(selected))
            val alpha = when {
                pos == selected -> Ink.Alpha.WashStrong
                selectedValue != 0 && game.current[pos] == selectedValue -> Ink.Alpha.Wash
                isPeer -> Ink.Alpha.Hair * 0.55f
                else -> 0f
            }
            if (alpha > 0f) {
                drawRect(
                    color = Ink.Black.copy(alpha = alpha),
                    topLeft = Offset(r.left, r.top),
                    size = Size(r.width, r.height),
                )
            }
        }

        // 1.5) 光标落纸：换格瞬间在格子上洇一层淡墨再退去（画在格线之下，格线不受影响）
        if (cursorLand > 0f && selected != null) {
            val r = cellRect(selected)
            drawRect(
                color = Ink.Black.copy(alpha = Ink.Alpha.Wash * 1.6f * cursorLand),
                topLeft = Offset(r.left, r.top),
                size = Size(r.width, r.height),
            )
        }

        // 2) 格线：细线 + 每 3 格加粗（宫线）
        val thin = DesignTokens.Stroke.Grid.toPx()
        val bold = DesignTokens.Stroke.GridBold.toPx()
        for (i in 1..8) {
            val isBox = i % 3 == 0
            val p = i * cellPx
            val w = if (isBox) bold else thin
            val a = if (isBox) Ink.Alpha.LineSoft else Ink.Alpha.Hair
            inkLine(Offset(p, 0f), Offset(p, cellPx * 9f), w, Ink.Black, seed = i, alpha = a)
            inkLine(Offset(0f, p), Offset(cellPx * 9f, p), w, Ink.Black, seed = 100 + i, alpha = a)
        }
        // 外框：手绘粗框，让整张棋盘像"画"在纸上的表格
        inkRoundRect(
            rect = Rect(1f, 1f, cellPx * 9f - 1f, cellPx * 9f - 1f),
            radiusPx = DesignTokens.Radius.Cell.toPx(),
            widthPx = DesignTokens.Stroke.Frame.toPx(),
            color = Ink.Black,
            seed = 7,
            alpha = Ink.Alpha.Line,
        )

        // 3) 数字与笔记
        for (pos in 0..80) {
            val r = cellRect(pos)
            val value = game.current[pos]
            if (value != 0) {
                // 「题面是印的，你填的是写的」——两类数字用**四种信号**分开，任取其一都看得出来：
                //   ① **字框**：给定数字套一枚手绘方框（专属标识符，见 drawTypeBox）；
                //   ② 墨的浓淡：给定用一墨（最浓），玩家填入用二墨；
                //   ③ 字形：给定走数字族 Nunito（印刷体），玩家填入走正文族霞鹜文楷（手写体）；
                //   ④ 读屏文案（cellA11yLabel）也分别念「给定」/「填入」。
                // 用字形而不是字重，是因为包里只打了一份 Nunito（Regular）：请求 Bold 会退回系统字体，
                // 反而让"给定数字"换了一副字形，看起来像 bug。
                val isGiven = Sudoku.isGiven(game, pos)
                if (isGiven) drawTypeBox(r, cellPx, seed = pos * 3 + 5)
                val color = if (isGiven) Ink.Black else Ink.Grey
                val fontSizePx = cellPx * 0.52f
                val layout = valueCache.cached(InkTextKey(value, color.value, fontSizePx.toInt(), isGiven)) {
                    textMeasurer.measure(
                        value.toString(),
                        Ink.digitStyle(
                            fontSizePx.toSp(),
                            color,
                            family = if (isGiven) Ink.FontDigits else Ink.FontText,
                        ),
                    )
                }
                val topLeft = Offset(
                    r.left + (cellPx - layout.size.width) / 2f,
                    r.top + (cellPx - layout.size.height) / 2f,
                )
                drawText(layout, topLeft = topLeft)

                // 冲突数字套手绘圈（不靠颜色也能看出来）
                if (conflicts[pos]) {
                    val center = Offset(r.left + cellPx / 2f, r.top + cellPx / 2f)
                    val radius = cellPx * 0.34f
                    val w = DesignTokens.Stroke.Bold.toPx()
                    drawCircle(Ink.Black.copy(alpha = Ink.Alpha.Line), radius, center, style = Stroke(width = w))
                    drawCircle(
                        Ink.Black.copy(alpha = Ink.Alpha.LineSoft),
                        radius + 0.8f,
                        Offset(center.x + 0.6f, center.y - 0.4f),
                        style = Stroke(width = w * 0.5f),
                    )
                }
                continue
            }

            if (showNotes) {
                val mask = notes[pos]
                val fontSizePx = cellPx * 0.24f
                if (mask != 0) {
                    // 玩家自己的笔记优先：那是他写下的判断，不该被系统候选盖住
                    for (digit in 1..9) {
                        if ((mask and (1 shl (digit - 1))) == 0) continue
                        val layout = noteCache.cached(InkTextKey(digit, Ink.Light.value, fontSizePx.toInt(), false)) {
                            textMeasurer.measure(digit.toString(), Ink.digitStyle(fontSizePx.toSp(), Ink.Light))
                        }
                        drawText(layout, topLeft = miniTopLeft(r, digit))
                    }
                } else if (hintCandidates) {
                    // 候选提示：所有空格直接写出规则允许的数字，用半透明灰（比笔记更淡，读作"背景信息"）
                    val legal = candidateMasks[pos]
                    if (legal != 0) {
                        for (digit in 1..9) {
                            if ((legal and (1 shl (digit - 1))) == 0) continue
                            val layout = noteCache.cached(InkTextKey(digit, CandidateColor.value, fontSizePx.toInt(), false)) {
                                textMeasurer.measure(
                                    digit.toString(),
                                    Ink.digitStyle(fontSizePx.toSp(), CandidateColor),
                                )
                            }
                            drawText(layout, topLeft = miniTopLeft(r, digit))
                        }
                    }
                }
            }
        }

        // 4) 选中框：常规实线 / 笔记模式虚线（线型区分，不用颜色）
        if (selected != null) {
            val r = cellRect(selected)
            val stroke = DesignTokens.Stroke.Selection.toPx()
            val inset = stroke / 2f
            inkRoundRect(
                rect = Rect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset),
                radiusPx = DesignTokens.Radius.Cell.toPx(),
                widthPx = stroke,
                color = Ink.Black,
                seed = selected + 3,
                alpha = Ink.Alpha.Line,
                dashed = noteMode,
            )
            if (!noteMode) {
                inkRoundRect(
                    rect = Rect(
                        r.left + inset + stroke,
                        r.top + inset + stroke,
                        r.right - inset - stroke,
                        r.bottom - inset - stroke,
                    ),
                    radiusPx = DesignTokens.Radius.Cell.toPx(),
                    widthPx = DesignTokens.Stroke.Hair.toPx(),
                    color = Ink.Black,
                    seed = selected + 9,
                    alpha = Ink.Alpha.LineSoft,
                )
            }
        }
    }
}

/**
 * 格子级语义：9×9 = 81 个语义节点，读屏可逐格朗读「行列 + 状态 + 值 / 笔记」。
 *
 * 两个刻意的取舍：
 * - **不做成可 Tab 聚焦**：81 个 Tab 停靠点会毁掉键盘体验（本作的键盘模型是方向键移动选中格）；
 *   而读屏用户用"朗读下一项"即可遍历——语义节点不需要 `focusable()` 也能被 TalkBack / NVDA 到达；
 * - **不朗读系统候选**（`hintCandidates`）：候选是冗余通道（悬浮面板与键盘都能拿到），
 *   逐格念 9 个候选会把行列与值淹没。玩家**自己记的笔记**要念——那是他的判断。
 */
@Composable
private fun BoardCellSemantics(
    game: Game,
    notes: IntArray,
    conflicts: BooleanArray,
    selected: Int?,
    paused: Boolean = false,
    strings: Strings = stringsFor(AppLanguage.ZhCn),
    modifier: Modifier = Modifier,
) {
    // 暂停 = 遮题：整层语义一起清空（clearAndSetSemantics 会连子树一起屏蔽），
    // 读屏此时只能读到父节点的整盘文案（含"已暂停"），不会逐格念出答案。
    Column(modifier = if (paused) modifier.clearAndSetSemantics {} else modifier) {
        for (row in 0..8) {
            // 用 `weight(1f)` 而不是 `fillMaxHeight(1f/9f)`：**Column 给子项的是"剩余"空间**，
            // `fillMaxHeight(1/9)` 于是逐行缩水（9 行只铺满约 65%），语义格与画出来的格就错位了——
            // 读屏念"第 5 行第 5 列"，读屏用户点下去却选到第 4 行第 4 列。
            // 这个 bug 长期没被发现，是因为 `BoardCanvasUiTest` 只点了**第 2 行第 2 列**：
            // 前两三行的偏差还不到一格，正好蒙对。`weight` 按固定总量等分，不受顺序影响。
            Row(modifier = Modifier.weight(1f)) {
                for (col in 0..8) {
                    val pos = row * 9 + col
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics {
                                contentDescription = cellA11yLabel(strings, game, pos, notes, conflicts, selected)
                            },
                    )
                }
            }
        }
    }
}

/**
 * 单格的读屏文案（**纯函数**，便于单测）。
 *
 * 结构：`第 R 行第 C 列` + 选中状态 + 内容（给定 / 填入 / 空 + 笔记）+ 冲突。
 * 例：`第 1 行第 1 列，已选中，给定 5`、`第 3 行第 5 列，空，笔记 1、3`、`第 4 行第 2 列，填入 7，与同行列宫重复`。
 */
internal fun cellA11yLabel(
    strings: Strings,
    game: Game,
    pos: Int,
    notes: IntArray,
    conflicts: BooleanArray,
    selected: Int?,
): String {
    val head = strings.cellPos.format(Sudoku.rowOf(pos) + 1, Sudoku.colOf(pos) + 1)
    val selectedText = if (pos == selected) strings.cellSelected else ""
    val value = game.current[pos]
    val body = when {
        value == 0 -> {
            val noteText = notesText(notes[pos], strings.notesSeparator)
            // 有笔记时是「空，笔记 1、3」——"空"与"笔记"两段都念，玩家才知道这格还能填
            if (noteText.isEmpty()) strings.cellEmpty else strings.cellEmpty + strings.cellNotes.format(noteText)
        }
        Sudoku.isGiven(game, pos) -> strings.cellGiven.format(value)
        else -> strings.cellFilled.format(value)
    }
    val conflictText = if (conflicts[pos]) strings.cellConflict else ""
    return head + selectedText + body + conflictText
}

/**
 * 笔记数字串：`1、3、9`（中文）或 `1, 3, 9`（英文）——分隔符按语言取（[Strings.notesSeparator]），
 * 无笔记时为空串。
 */
private fun notesText(mask: Int, separator: String): String {
    val digits = (1..9).filter { digit -> (mask and (1 shl (digit - 1))) != 0 }
    return digits.joinToString(separator)
}

/** 文本缓存键：数字 + 颜色 + 字号(px) + 是否加粗。 */
private data class InkTextKey(
    val digit: Int,
    val color: ULong,
    val sizePx: Int,
    val bold: Boolean,
)

/**
 * 候选数字的墨色：**半透明灰**。
 * 与玩家笔记（[Ink.Light] 实墨）拉开层次——候选是"系统算出来的背景信息"，笔记是"自己写下的判断"。
 */
private val CandidateColor: Color get() = Ink.Grey.copy(alpha = Ink.Alpha.Hint)

/**
 * 题面数字的「字框」标识符：给给定数字套一枚手绘圆角方框（铅字落在自己的字框里）。
 *
 * **为什么要给题面数字一枚专属标识，而不是继续调字形 / 墨色**：
 * 字形（印刷体 vs 手写体）与墨色浓淡都属于**中心视觉**——必须逐格看清、逐个数字比对才分得出来；
 * 玩家真正想要的是"一眼扫过就知道哪些是我填的"。给题面数字一个**独有形状**，
 * 才能让"一屏之内多数数字带框、只有少数不带框"在余光里就成立。
 *
 * **为什么不做"整格压印底"**（试过并与 [Ink.Alpha.Wash] 撞车，见 `docs/02` §5.1）：
 * 亮度轴已经被临时高亮占满了——选中用 [Ink.Alpha.WashStrong]、同数字用 [Ink.Alpha.Wash]、
 * 同行列宫再用一档。常驻标识若也走亮度，等于往同一条通道里挤第三层信息，
 * 结果是"选中第几行"和"哪些是题面"互相糊掉（截图实测如此）。
 * **常驻标识走形状、临时状态走亮度**，两条通道互不干扰，是这一版真正立住的地方。
 *
 * **为什么用 [inkRoundRect] 而不是 `drawRoundRect`**：与标题框、面板用同一支笔，
 * 微弯 + 叠墨的手绘感一致；纯几何圆角框在全手绘画面里会显得像贴上去的控件。
 */
private fun DrawScope.drawTypeBox(rect: Rect, cellPx: Float, seed: Int) {
    val side = cellPx * 0.74f
    inkRoundRect(
        rect = Rect(
            left = rect.left + (cellPx - side) / 2f,
            top = rect.top + (cellPx - side) / 2f,
            right = rect.left + (cellPx + side) / 2f,
            bottom = rect.top + (cellPx + side) / 2f,
        ),
        radiusPx = DesignTokens.Radius.Mark.toPx(),
        widthPx = DesignTokens.Stroke.Thin.toPx(),
        color = Ink.Black,
        seed = seed,
        alpha = Ink.Alpha.TypeBox,
    )
}

/** 带容量保护的跨帧文本测量缓存（连续拖拽缩放窗口时不会无限增长）。 */
private fun MutableMap<InkTextKey, TextLayoutResult>.cached(
    key: InkTextKey,
    measure: () -> TextLayoutResult,
): TextLayoutResult {
    if (size > MAX_CACHE_ENTRIES) clear()
    return getOrPut(key, measure)
}

private const val MAX_CACHE_ENTRIES = 256
