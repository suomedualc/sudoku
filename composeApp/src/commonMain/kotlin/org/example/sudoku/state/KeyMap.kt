package org.example.sudoku.state

/**
 * 可自定义键位的**令牌**（不是平台虚拟键码）。
 *
 * - 可打印键 → **大写字符**（`"W"`、`"0"`）
 * - 特殊键 → **名字**（`"DirectionUp"`、`"Space"`、`"Backspace"`、`"Delete"`）
 *
 * 为什么不用虚拟键码：`state/` 不许依赖平台 / Compose API，且键码换平台就失效；
 * 令牌是**纯文本**，存档可读、判据可单测、换机器不丢设置。
 */
object KeyTokens {
    const val UP = "DirectionUp"
    const val DOWN = "DirectionDown"
    const val LEFT = "DirectionLeft"
    const val RIGHT = "DirectionRight"
    const val SPACE = "Space"
    const val BACKSPACE = "Backspace"
    const val DELETE = "Delete"
}

/** 可绑定的五个动作。顺序 = 设置抽屉里的排列顺序，也是冲突判定时的优先级。 */
enum class KeyAction { Up, Down, Left, Right, Erase }

/**
 * 玩家的键位配置（**跨对局保留**，随存档落盘）。
 *
 * 默认：方向键移动 + `0` 擦除。另有三条兜底写在 `GameScreen.handleKeyEvent`：
 * 退格 / Delete **始终**可擦除、方向键**始终**可移动——
 * 键位设置是为了"更好用"，不能成为"把自己锁死在门外"的方式。
 */
data class KeyMap(
    val up: String = KeyTokens.UP,
    val down: String = KeyTokens.DOWN,
    val left: String = KeyTokens.LEFT,
    val right: String = KeyTokens.RIGHT,
    val erase: String = "0",
) {
    fun token(action: KeyAction): String = when (action) {
        KeyAction.Up -> up
        KeyAction.Down -> down
        KeyAction.Left -> left
        KeyAction.Right -> right
        KeyAction.Erase -> erase
    }

    /**
     * 把 [action] 绑到 [token]。
     *
     * **同一个键不能兼任两个功能**：先把别的动作上撞车的键复位为默认，再写新的——
     * 否则玩家把"上"设成 W、又把"左"设成 W 时，会得到一个"按 W 有时上有时左"的谜之状态。
     */
    fun bind(action: KeyAction, token: String): KeyMap {
        val cleared = KeyMap(
            up = if (action != KeyAction.Up && up == token) KeyTokens.UP else up,
            down = if (action != KeyAction.Down && down == token) KeyTokens.DOWN else down,
            left = if (action != KeyAction.Left && left == token) KeyTokens.LEFT else left,
            right = if (action != KeyAction.Right && right == token) KeyTokens.RIGHT else right,
            erase = if (action != KeyAction.Erase && erase == token) DEFAULT.erase else erase,
        )
        return when (action) {
            KeyAction.Up -> cleared.copy(up = token)
            KeyAction.Down -> cleared.copy(down = token)
            KeyAction.Left -> cleared.copy(left = token)
            KeyAction.Right -> cleared.copy(right = token)
            KeyAction.Erase -> cleared.copy(erase = token)
        }
    }

    /** 令牌 → 移动方向（dRow, dCol）；不是移动键则返回 null。 */
    fun move(token: String): Pair<Int, Int>? = when (token) {
        up -> -1 to 0
        down -> 1 to 0
        left -> 0 to -1
        right -> 0 to 1
        else -> null
    }

    fun isErase(token: String): Boolean = token == erase

    /**
     * 这个令牌能不能绑给 [action]。
     *
     * **1–9 一律不绑**：数字键是"填数"的专属通道，绑走之后"按 5"就填不了 5 了——
     * 与其让玩家配出一个自己都解释不清的键盘，不如当场拒掉并说明原因（`0` 可以绑，它不参与填数）。
     */
    fun accepts(action: KeyAction, token: String): Boolean = token !in FILL_DIGITS

    fun encode(): String = listOf(up, down, left, right, erase).joinToString(",")

    companion object {
        val DEFAULT = KeyMap()

        /** 填数专属：1–9 绑给别的功能会让数字键填不了数（见 [accepts]）。 */
        private val FILL_DIGITS: Set<String> = ('1'..'9').map { it.toString() }.toSet()

        /**
         * 存档是**外部输入**：段数不对 / 有空段就整体取默认，
         * 不让一个坏字段把整套键位废掉（与 [String?.toFlag] "宽松取默认"同一条策略）。
         * 逐项校验 "1–9 拒绑"：被数字占据的槽位回落到该动作的默认键位，
         * 不让手改 / 损坏的存档绕过 [accepts] 的不变量（否则"按 5"就填不了 5）。
         */
        fun decode(text: String?): KeyMap {
            val parts = text?.split(',')?.map { it.trim() } ?: return DEFAULT
            if (parts.size != 5 || parts.any { it.isEmpty() }) return DEFAULT
            fun valid(token: String, fallback: String): String = if (token in FILL_DIGITS) fallback else token
            return KeyMap(
                up = valid(parts[0], KeyTokens.UP),
                down = valid(parts[1], KeyTokens.DOWN),
                left = valid(parts[2], KeyTokens.LEFT),
                right = valid(parts[3], KeyTokens.RIGHT),
                erase = valid(parts[4], DEFAULT.erase),
            )
        }
    }
}
