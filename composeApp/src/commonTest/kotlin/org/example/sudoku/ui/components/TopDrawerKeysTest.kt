package org.example.sudoku.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 顶部抽屉的按键仲裁：「覆盖层 + 快捷键」最容易出问题的规则都在这里钉死。
 *
 * 背景：Compose 的 `onPreviewKeyEvent` 父先于子，页面根节点一定先拿到按键，
 * 所以"抽屉打开后底下的页面还会不会响应"完全取决于这套规则。
 */
class TopDrawerKeysTest {

    // ---------------------------------------------------------------- 纯策略
    @Test
    fun drawerOwnShortcutIsConsumedAndKeepsDrawerOpen() {
        val decision = TopDrawerKeys.decide(
            isKeyDown = true,
            isEscape = true,
            dismissible = true,
            drawerHandled = true, // 例如通关抽屉把 Esc 解释成"返回首页"
        )
        assertTrue(decision.consumed)
        assertFalse(decision.dismiss, "抽屉内已处理的键不该再触发关闭")
    }

    @Test
    fun escapeDismissesWhenDrawerCanBeClosed() {
        val decision = TopDrawerKeys.decide(
            isKeyDown = true,
            isEscape = true,
            dismissible = true,
            drawerHandled = false,
        )
        assertEquals(true, decision.consumed)
        assertEquals(true, decision.dismiss)
    }

    @Test
    fun escapeKeepsDrawerWhenItMustBeAnswered() {
        val decision = TopDrawerKeys.decide(
            isKeyDown = true,
            isEscape = true,
            dismissible = false, // 通关结算：必须明确选「再来一局」或「返回首页」
            drawerHandled = false,
        )
        assertTrue(decision.consumed)
        assertFalse(decision.dismiss)
    }

    @Test
    fun otherKeysAreSwallowedWhileDrawerIsOpen() {
        // 数字键 / 未处理的方向键：模态吞掉，绝不落到下面的棋盘或菜单
        val decision = TopDrawerKeys.decide(
            isKeyDown = true,
            isEscape = false,
            dismissible = true,
            drawerHandled = false,
        )
        assertTrue(decision.consumed, "抽屉打开时未处理的键也必须消费")
        assertFalse(decision.dismiss)
    }

    @Test
    fun keyUpIsSwallowedWithoutDismissing() {
        val decision = TopDrawerKeys.decide(
            isKeyDown = false,
            isEscape = true,
            dismissible = true,
            drawerHandled = false,
        )
        assertTrue(decision.consumed)
        assertFalse(decision.dismiss, "抬起事件不应关闭抽屉，否则一次 Esc 会被处理两遍")
    }

    // ---------------------------------------------------------------- 控制器
    @Test
    fun controllerDoesNotConsumeWhenNoDrawerIsOpen() {
        val drawer = TopDrawerController()
        assertFalse(drawer.isOpen)
        // 没有抽屉：任何键都交回页面处理（否则页面快捷键会全体失灵）
        assertFalse(drawer.handleKeyFlags(isKeyDown = true, isEscape = false, drawerHandled = false))
        assertFalse(drawer.handleKeyFlags(isKeyDown = true, isEscape = true, drawerHandled = true))
    }

    @Test
    fun controllerConsumesEverythingAndClosesOnEscape() {
        val drawer = TopDrawerController()
        drawer.open("menu.difficulty")
        assertTrue(drawer.isOpen("menu.difficulty"))

        // 抽屉内的方向键（已处理）：消费、不关闭
        assertTrue(drawer.handleKeyFlags(isKeyDown = true, isEscape = false, drawerHandled = true))
        assertTrue(drawer.isOpen)

        // 数字键（未处理）：消费但不关闭
        assertTrue(drawer.handleKeyFlags(isKeyDown = true, isEscape = false, drawerHandled = false))
        assertTrue(drawer.isOpen)

        // Esc：消费并关闭
        assertTrue(drawer.handleKeyFlags(isKeyDown = true, isEscape = true, drawerHandled = false))
        assertFalse(drawer.isOpen)
    }

    @Test
    fun modalDrawerIgnoresEscapeAndKeepsConsuming() {
        val drawer = TopDrawerController()
        drawer.open("game.win", dismissible = false)
        assertFalse(drawer.dismissible)

        assertTrue(drawer.handleKeyFlags(isKeyDown = true, isEscape = true, drawerHandled = false))
        assertTrue(drawer.isOpen("game.win"), "通关抽屉不吃 Esc 关闭，只能走按钮或抽屉内快捷键")
    }

    @Test
    fun openingAnotherDrawerReplacesTheCurrentOne() {
        val drawer = TopDrawerController()
        drawer.open("menu.difficulty")
        drawer.open("menu.exit")
        assertFalse(drawer.isOpen("menu.difficulty"))
        assertTrue(drawer.isOpen("menu.exit"))
        drawer.close()
        assertFalse(drawer.isOpen)
    }
}
