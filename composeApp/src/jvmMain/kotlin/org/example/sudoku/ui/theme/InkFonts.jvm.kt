package org.example.sudoku.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import java.io.File

/**
 * 桌面端字形：**随包字体优先**，取不到才退到系统字体。
 *
 * 为什么改成随包（此前是探测系统字体）：
 * - "一行 wid 命令 glean 出的汉字"在不同机器上会落到不同字体——上海处看起来是硬笔楷，
 *   到别人机器可能变成宋体甚至黑体，"手写纸 · 简约油墨"的调性就散了；
 * - 字源稳定之后，**字形分享不再依赖用户装了什么**，评审与截图能在任何机器上复现。
 *
 * 资产由 `tools/prepare-fonts.ps1` 生成（`composeApp/src/jvmMain/resources/fonts/`）：
 * - `SourceSerifPro-Regular.ttf` 拉丁字与数字（OFL 1.1）；
 * - `LXGWWenKaiSubset.ttf` 汉字——文楷 Screen 版按 UI 实际用字**子集化**（18.2 MB → 0.34 MB）；
 * - 两份授权文本随二进制一起归档（`OFL-*.txt`）。
 *
 * Compose 桌面端只能从 **File** 加载字体，资源又在 jar 里，所以首次使用把资源
 * 解到应用数据目录（一次性、仅几百 KB），之后直接读盘。
 */
private val BundledFontNames: List<String> = listOf(
    "SourceSerifPro-Regular.ttf",
    "LXGWWenKaiSubset.ttf",
)

private const val RESOURCE_DIR = "/fonts"

private val bundledFontFamily: FontFamily? by lazy {
    val dir = fontCacheDir() ?: return@lazy null
    val fonts = BundledFontNames.mapNotNull { name -> extractFont(name, dir)?.let { Font(it) } }
    if (fonts.size == BundledFontNames.size) FontFamily(fonts) else null
}

/**
 * 系统字体兜底（机器没有随包字体时的最后一道）。
 * 顺序按"与这个项目调性的贴合度"排：**霞鹜文楷**在前（用户推荐 + Screen 版为屏幕优化），
 * 方正硬笔楷书、系统楷体紧随其后；全都没有就返回 null，由调用方回退衬线体。
 */
private val SystemFontCandidates: List<String> = listOf(
    // Windows
    "C:/Windows/Fonts/LXGWWenKaiScreen.ttf",        // 霞鹜文楷 Screen（OFL）
    "C:/Windows/Fonts/LXGWWenKaiGBFusion-Regular.ttf",
    "C:/Windows/Fonts/FZYTK.TTF",                   // 方正硬笔楷书
    "C:/Windows/Fonts/simkai.ttf",                  // 楷体
    "C:/Windows/Fonts/STKAITI.TTF",                 // 华文楷体
    // macOS
    "/Library/Fonts/Kaiti.ttc",
    "/System/Library/Fonts/Supplemental/Kaiti.ttc",
    // Linux（常见发行版路径）
    "/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc",
    "/usr/share/fonts/truetype/arphic/ukai.ttc",
)

private val systemFontFamily: FontFamily? by lazy {
    SystemFontCandidates.firstNotNullOfOrNull { path ->
        runCatching {
            val file = File(path)
            if (file.isFile) FontFamily(Font(file)) else null
        }.getOrNull()
    }
}

/** 把资源里的字体解到应用数据目录并返回可读文件；任何失败返回 null（不拖慢启动、不崩）。 */
private fun extractFont(name: String, dir: File): File? = try {
    val target = File(dir, name)
    if (!target.isFile) {
        val input = InkFontsJvm::class.java.getResourceAsStream("$RESOURCE_DIR/$name")
        if (input != null) {
            input.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
        }
    }
    if (target.isFile && target.length() > 0) target else null
} catch (_: Throwable) {
    null
}

private fun fontCacheDir(): File? = try {
    val dir = File(System.getProperty("user.home"), ".sudoku-ink/fonts")
    if (dir.isDirectory || dir.mkdirs()) dir else null
} catch (_: Throwable) {
    null
}

/** 仅作为取资源的锚点（不要删：资源路径解析依赖它所在的类加载器）。 */
private object InkFontsJvm

actual fun platformInkFontFamily(): FontFamily? = bundledFontFamily ?: systemFontFamily
