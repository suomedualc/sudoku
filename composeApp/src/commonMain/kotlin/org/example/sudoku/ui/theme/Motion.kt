package org.example.sudoku.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * 是否应减少动态效果（系统级"减少动画 / 减少动态效果"设置，或显式覆盖）。
 *
 * 各平台实现见对应 source set 的 `Motion` 实际实现；桌面端会读系统设置并允许用
 * `-Dsudoku.reduceMotion=true` / 环境变量 `SUDOKU_REDUCE_MOTION` 覆盖（便于验收与截图对比）。
 *
 * **判定时机**：首次读取时判定一次并缓存（不在启动时做，避免拖慢冷启动）；
 * 运行期改系统设置需重启应用才生效——与桌面应用的常见做法一致。
 */
expect fun prefersReducedMotion(): Boolean

/**
 * 动效开关的**注入点**：默认取平台判定，测试可用 `CompositionLocalProvider` 覆盖。
 *
 * 之所以要这个注入点：平台 API 在跨平台测试源集里调不到，而"减少动效时时长必须是 0"
 * 恰恰是最容易写漏的一条（写成"缩短一半"就白做了）。
 */
val LocalReduceMotion: CompositionLocal<Boolean> = compositionLocalOf { prefersReducedMotion() }

/**
 * 纯函数：基准时长 → 实际时长。**减少动效时一律 0（瞬时）**。
 *
 * 不做"缩短一半"这类折中：动画的价值是"让人看清发生了什么"，看不清就不如没有——
 * 半速动画对前庭敏感的用户更难受。
 */
fun motionDurationMs(baseMs: Int, reduceMotion: Boolean): Int = if (reduceMotion) 0 else baseMs

/** 组合内读取最终时长（跟随 [LocalReduceMotion]）。 */
@Composable
fun motionDurationMs(baseMs: Int): Int = motionDurationMs(baseMs, LocalReduceMotion.current)
