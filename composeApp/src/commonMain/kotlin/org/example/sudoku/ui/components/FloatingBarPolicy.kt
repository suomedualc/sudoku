package org.example.sudoku.ui.components

/** 浮动操作条贴在棋盘的哪一侧。 */
enum class BarSide { Top, Bottom }

/**
 * 撤销 / 重做浮动条的位置策略：**跟着鼠标走**。
 *
 * 鼠标靠近棋盘**下方**区域 → 条贴到棋盘正下方；靠近**上方**区域 → 自动换到正上方。
 * 这样它总落在鼠标附近，不必把指针从棋盘移到屏幕另一头的控制栏。
 *
 * 为什么要**滞回**而不是简单按中线切：鼠标停在中线附近轻微晃动时条会来回跳，
 * 一跳，指着的"撤销"就换成了"重做"——误触代价不小。
 * 因此只在明确越过 35% / 65% 两条线时才换边，中间那 30% 是"保持原样"的死区。
 *
 * 纯函数：不依赖 Compose 运行时，单测直接盯。
 */
object FloatingBarPolicy {

    /** 没有指针信息时（触屏 / 纯键盘）的默认侧：下方——离手最近，也不挡题面的第一行。 */
    val Default: BarSide = BarSide.Bottom

    /** 越过这条线（相对棋盘高度的比例）就认为鼠标在上方区域。 */
    private const val TOP_THRESHOLD = 0.35f

    /** 越过这条线就认为鼠标在下方区域。 */
    private const val BOTTOM_THRESHOLD = 0.65f

    /**
     * 算出条应该在哪一侧。
     *
     * @param previous 当前所在侧（死区内保持不变）
     * @param pointerY 鼠标在棋盘容器内的纵坐标（px）；`null` = 还没有指针信息
     * @param boardTop 棋盘容器顶边的纵坐标（px）
     * @param boardHeight 棋盘容器高度（px）；非正时视为"量不到"，保持原样
     */
    fun next(previous: BarSide, pointerY: Float?, boardTop: Float, boardHeight: Float): BarSide {
        if (pointerY == null || boardHeight <= 0f) return previous
        val relative = (pointerY - boardTop) / boardHeight
        return when {
            relative < TOP_THRESHOLD -> BarSide.Top
            relative > BOTTOM_THRESHOLD -> BarSide.Bottom
            else -> previous
        }
    }
}
