package org.example.sudoku.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 键位配置单测：令牌是**字符串**，所以整套逻辑（绑定 / 冲突 / 匹配 / 落盘）都能纯函数地测，
 * 不需要构造平台按键事件（那正是把换算留在 `ui/KeyToken` 的原因）。
 */
class KeyMapTest {

    @Test
    fun defaultsAreArrowsAndZero() {
        val map = KeyMap()
        assertEquals(KeyTokens.UP, map.up)
        assertEquals(KeyTokens.DOWN, map.down)
        assertEquals(KeyTokens.LEFT, map.left)
        assertEquals(KeyTokens.RIGHT, map.right)
        assertEquals("0", map.erase)
    }

    @Test
    fun bindWritesTheTokenAndMoveMatchesIt() {
        var map = KeyMap()
        map = map.bind(KeyAction.Up, "W")
        map = map.bind(KeyAction.Left, "A")

        assertEquals("W", map.up)
        assertEquals(-1 to 0, map.move("W"), "W 应对应向上")
        assertEquals(0 to -1, map.move("A"), "A 应对应向左")
        assertNull(map.move(KeyTokens.UP), "改绑之后原方向键不再移动（除非另有绑定）")
    }

    @Test
    fun bindingTheSameKeyToTwoActionsClearsTheOther() {
        // 同一个键不能兼任两个功能：否则按 W 有时上有时左，玩家没法预测
        var map = KeyMap().bind(KeyAction.Up, "W")
        map = map.bind(KeyAction.Left, "W")

        assertEquals("W", map.left)
        assertEquals(KeyTokens.UP, map.up, "被抢走的「上」应复位为默认方向键")
        assertEquals(1, listOf(map.up, map.down, map.left, map.right).count { it == "W" })
    }

    @Test
    fun eraseKeyIsRecognized() {
        val map = KeyMap().bind(KeyAction.Erase, "X")
        assertTrue(map.isErase("X"))
        assertFalse(map.isErase("0"), "改绑之后 0 不再是擦除键")
    }

    @Test
    fun digitsOneToNineCannotBeBound() {
        // 数字键是填数的专属通道：绑走之后"按 5"就填不了 5 了
        val map = KeyMap()
        for (d in '1'..'9') {
            assertFalse(map.accepts(KeyAction.Up, d.toString()), "$d 不该能绑定")
        }
        assertTrue(map.accepts(KeyAction.Up, "W"))
        assertTrue(map.accepts(KeyAction.Erase, "0"), "0 不参与填数，可以绑给擦除")
        assertTrue(map.accepts(KeyAction.Erase, KeyTokens.SPACE))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val map = KeyMap().bind(KeyAction.Up, "W").bind(KeyAction.Erase, "X")
        assertEquals(map, KeyMap.decode(map.encode()))
    }

    @Test
    fun malformedTextFallsBackToDefaults() {
        // 存档是外部输入：一个坏字段不该把整套键位废掉
        assertEquals(KeyMap.DEFAULT, KeyMap.decode(null))
        assertEquals(KeyMap.DEFAULT, KeyMap.decode(""))
        assertEquals(KeyMap.DEFAULT, KeyMap.decode("W"))
        assertEquals(KeyMap.DEFAULT, KeyMap.decode("W,A,S,,X"))
        assertEquals(KeyMap.DEFAULT, KeyMap.decode("W,A,S,D,X,Y"))
    }

    @Test
    fun labelsAreShortAndReadable() {
        assertEquals("↑", keyTokenLabel(KeyTokens.UP))
        assertEquals("↓", keyTokenLabel(KeyTokens.DOWN))
        assertEquals("←", keyTokenLabel(KeyTokens.LEFT))
        assertEquals("→", keyTokenLabel(KeyTokens.RIGHT))
        assertEquals("空格", keyTokenLabel(KeyTokens.SPACE))
        assertEquals("退格", keyTokenLabel(KeyTokens.BACKSPACE))
        assertEquals("W", keyTokenLabel("W"), "字母 / 数字原样显示")
    }
}
