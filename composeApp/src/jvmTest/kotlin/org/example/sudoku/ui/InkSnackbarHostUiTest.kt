package org.example.sudoku.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.example.sudoku.ui.components.InkSnackbarHost
import org.example.sudoku.ui.components.InkSnackbarHostState
import kotlin.test.Test

/**
 * 墨条**固定停留时长**的自动验收（用户报告过的缺陷：连续点击提示键后，
 * "已填入正确答案"提示框的消失时间随点击次数累加——旧实现是"挂起排队"，
 * N 条消息要 N × 2.4s 才走完）。
 *
 * 顶替式显示后，每条消息的停留时长都从**它自己出现的那一刻**起算：
 * 最后一条显示满 2.4s 即退场，与之前点过多少次无关。用测试时钟把这条钉住。
 */
@OptIn(ExperimentalTestApi::class)
class InkSnackbarHostUiTest {

    @Test
    fun rapidMessagesDoNotExtendTheDisplayTime() = runComposeUiTest {
        val state = InkSnackbarHostState()
        mainClock.autoAdvance = false
        setContent { InkSnackbarHost(state) }
        mainClock.advanceTimeBy(1) // 先走一帧，让初始组合就位

        // ① 单条：满 2.4s 即退场
        runOnIdle { state.show("第一条") }
        mainClock.advanceTimeBy(400)
        onNodeWithText("第一条").assertExists("显示期间墨条应当在场")
        mainClock.advanceTimeBy(2_200) // 距显示共 2.6s > 2.4s
        onNodeWithText("第一条").assertDoesNotExist() // 固定时长到点应当退场

        // ② 连续快速两条：第二条出现 1s 后第三条顶掉它；此后 2.4s 必须退场——
        //    旧的排队实现里此时（距第三条 2.4s、距第一条 3.4s）第三条还在排队
        runOnIdle { state.show("连续一") }
        mainClock.advanceTimeBy(1_000)
        runOnIdle { state.show("连续二") }
        mainClock.advanceTimeBy(100) // 推几帧让"顶替"文本组合出来（顺带消耗它 2.4s 中的 100ms）
        onNodeWithText("连续二").assertExists("新提示应当立即顶掉当前那条")
        mainClock.advanceTimeBy(2_500) // 2.4s 到点隐藏 + 180ms 退场动画走完
        onNodeWithText("连续二").assertDoesNotExist() // 连续点击不应让停留时长累加
    }
}
