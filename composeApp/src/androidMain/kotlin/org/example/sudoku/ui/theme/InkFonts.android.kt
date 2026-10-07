package org.example.sudoku.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import org.example.sudoku.R

/**
 * Android 端字体加载：与桌面同一套字体资产（霞鹜文楷 + Nunito，OFL 1.1），
 * 经 `androidMain/res/font` 打进 APK，`R.font` 引用——跨端字形一致，无系统探针、无兜底缺口。
 *
 * 数字族与桌面同规则：**Nunito 优先 + 霞鹜文楷兜底**（暂停时计时位的"已暂停"由文楷接住，
 * 不会渲染成豆腐块）。`Font(resId)` 构造失败理论上不可能（资源随包），仍用 runCatching 兜底：
 * 取不到时返回 null，调用方回退系统族。
 */
actual fun textFontFamily(): FontFamily? =
    runCatching { FontFamily(Font(R.font.lxgw_wen_kai)) }.getOrNull()

actual fun digitFontFamily(): FontFamily? = runCatching {
    FontFamily(Font(R.font.nunito), Font(R.font.lxgw_wen_kai))
}.getOrNull()
