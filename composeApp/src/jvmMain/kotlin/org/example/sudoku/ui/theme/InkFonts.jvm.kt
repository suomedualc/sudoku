package org.example.sudoku.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import java.io.File

/**
 * 桌面端手写体：按优先级探测系统中文字体文件，取到即用，取不到返回 null（调用方回退衬线体）。
 *
 * 说明与取舍：
 * - **不随包分发字体**（不增加仓库体积、不涉及字体再分发授权），只引用本机已安装字体；
 * - 首选「方正硬笔楷书」：硬笔楷书最贴近"手写 + 油墨"，且 3 MB 左右、笔画清晰易读；
 * - 其次「霞鹜文楷 Screen」（开源 OFL，观感温暖）；再次系统「楷体 / 华文楷体」；
 * - 探测失败（常见于精简版系统或 Linux 容器）时走衬线体回退，功能不受影响。
 */
private val CandidateFontFiles: List<String> = buildList {
    // Windows
    add("C:/Windows/Fonts/FZYTK.TTF")           // 方正硬笔楷书
    add("C:/Windows/Fonts/LXGWWenKaiScreen.ttf") // 霞鹜文楷 Screen
    add("C:/Windows/Fonts/simkai.ttf")           // 楷体
    add("C:/Windows/Fonts/STKAITI.TTF")          // 华文楷体
    // macOS
    add("/System/Library/Fonts/Supplemental/Kaiti.ttc")
    add("/Library/Fonts/Kaiti.ttc")
    // Linux（常见发行版路径）
    add("/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc")
    add("/usr/share/fonts/truetype/arphic/ukai.ttc")
}

private val cachedInkFont: FontFamily? by lazy {
    CandidateFontFiles.firstNotNullOfOrNull { path ->
        runCatching {
            val file = File(path)
            if (file.isFile) FontFamily(Font(file)) else null
        }.getOrNull()
    }
}

actual fun systemInkFontFamily(): FontFamily? = cachedInkFont
