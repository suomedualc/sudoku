package org.example.sudoku.ui.screens

import androidx.compose.ui.unit.Dp
import org.example.sudoku.ui.theme.DesignTokens

/*
 * 对局页布局判定：**竖屏**（棋盘 + 下方功能区纵向一列）或 **横屏**（棋盘左 + 键盘右双栏）。
 *
 * 从 GameScreen.kt 拆出（2026-10-24）：只搬位置、不改逻辑。
 */

/** 对局页布局形态。 */
internal enum class GameLayout { Portrait, Landscape }

/**
 * 布局形态判定（纯函数）：**宽 > 高 且 高 < [DesignTokens.Sizes.LandscapeMaxHeight]** 判为横屏双栏。
 *
 * 用宽高比较而不是平台 / 屏幕 API：桌面窗口、Android 横竖屏、折叠屏展开态共用同一套判据，
 * 也便于单测（不依赖真机旋转）。等宽高按竖屏（纵列）处理。
 *
 * **为什么要加高度上限**（`docs/10` §迭代 B2 的"新断点：高 < 480dp 且宽 > 高"）：
 * 双栏的前提是"纵向空间不够，把键盘挪到右边"。若只看"宽 > 高"，桌面默认窗口（1180×900）
 * 就会被判为横屏——那是个**高度充裕**的窗口，双栏反而把棋盘从 ~500dp 压到 ~400dp、
 * 还把 1–9 横排挤成九宫格，与 `docs/02` §7「对局页只有一条纵列」的设计相悖。
 * 加上 480dp 之后：手机横屏（高 360–430dp）走双栏，桌面窗口与平板横屏（高 ≥ 600dp）
 * 保持纵列，两边各得其所。
 */
internal fun gameLayout(width: Dp, height: Dp): GameLayout =
    if (width > height && height < DesignTokens.Sizes.LandscapeMaxHeight) {
        GameLayout.Landscape
    } else {
        GameLayout.Portrait
    }

