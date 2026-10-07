package org.example.sudoku.ui.theme

import android.provider.Settings
import org.example.sudoku.platform.AppGlobals

/**
 * Android 的"减少动态效果"判定：系统**动画时长缩放 = 0**（开发者选项里的
 * "动画程序时长缩放"全部关闭，或无障碍里的"移除动画"）时返回 true。
 *
 * 与桌面端同一条纪律：**首次读取时判定一次并缓存**，运行期改设置需重启应用。
 */
actual fun prefersReducedMotion(): Boolean {
    val context = AppGlobals.appContext ?: return false
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale == 0f
}
