package org.example.sudoku.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.io.File

/**
 * 桌面端字体加载：**打包字体优先，系统字体兜底**。
 *
 * - 正文：`fonts/LXGWWenKai-Regular.ttf`（霞鹜文楷，OFL 1.1）→ 系统楷书 → 调用方回退衬线体；
 * - 数字：`fonts/Nunito.ttf`（Nunito 变量字体，OFL 1.1）→ 调用方回退无衬线体。
 *
 * 加载方式说明（Compose Multiplatform 1.11 的桌面端）：
 * - 顶层工厂 `androidx.compose.ui.text.platform.Font` 只剩 `Font(identity)`（按系统字体名）
 *   与 `Font(file)`（按磁盘文件）两个入口，**没有**"按 classpath 资源 / 字节数组"的入口；
 * - 打包字体打进 jar 后不是磁盘文件，所以先把资源落到一个临时文件、再用 `Font(file)` 装载，
 *   与旧代码"从系统字体文件加载"是同一条 `FileFont` 路径，行为确定。
 * - 每次启动首帧才惰性解一次（`deleteOnExit` 自动清理），24MB 的一次性磁盘写可忽略。
 *
 * 授权：两份字体均以 **SIL OFL 1.1** 随包分发（原文见 `resources/fonts/OFL-*.txt`），
 * 打包的是**未修改的原版**，保留字体名合法。若后续为移动端做子集，需按 OFL 第 3 条
 * 处理保留字体名（霞鹜文楷的 OFL 声明保留「LXGW / 霞鹜」等名）。
 */
private val bundledWenKai: FontFamily? by lazy { loadResourceFont("fonts/LXGWWenKai-Regular.ttf") }

private val bundledNunito: FontFamily? by lazy { loadResourceFont("fonts/Nunito.ttf") }

private fun loadResourceFont(resource: String): FontFamily? {
    val bytes = runCatching {
        Ink::class.java.classLoader.getResourceAsStream(resource)?.use { it.readBytes() }
    }.getOrNull() ?: return null
    return runCatching {
        val file = File.createTempFile("sudoku-ink-font-", ".ttf")
        file.deleteOnExit()
        file.writeBytes(bytes)
        FontFamily(Font(file = file, weight = FontWeight.Normal, style = FontStyle.Normal))
    }.getOrNull()
}

/** 系统楷书文件（打包字体加载失败时的兜底，按平台路径探测）。 */
private val systemKaiTi: FontFamily? by lazy {
    SystemKaiTiFiles.firstNotNullOfOrNull { path ->
        runCatching {
            val file = File(path)
            if (file.isFile) {
                FontFamily(Font(file = file, weight = FontWeight.Normal, style = FontStyle.Normal))
            } else {
                null
            }
        }.getOrNull()
    }
}

actual fun textFontFamily(): FontFamily? = bundledWenKai ?: systemKaiTi

actual fun digitFontFamily(): FontFamily? = bundledNunito

private val SystemKaiTiFiles: List<String> = listOf(
    // Windows
    "C:/Windows/Fonts/FZYTK.TTF",             // 方正硬笔楷书
    "C:/Windows/Fonts/simkai.ttf",            // 楷体
    "C:/Windows/Fonts/STKAITI.TTF",           // 华文楷体
    // macOS
    "/System/Library/Fonts/Supplemental/Kaiti.ttc",
    "/Library/Fonts/Kaiti.ttc",
    // Linux
    "/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc",
    "/usr/share/fonts/truetype/arphic/ukai.ttc",
)
