package org.example.sudoku.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font as PlatformFont
import java.io.File

/**
 * 桌面端字体加载：**打包字体优先，系统字体兜底**。
 *
 * - 正文：`fonts/LXGWWenKai-Regular.ttf`（霞鹜文楷，OFL 1.1）→ 系统楷书 → 调用方回退衬线体；
 * - 数字：`fonts/Nunito.ttf`（Nunito 变量字体，OFL 1.1，**中文回退霞鹜文楷**）→ 调用方回退无衬线体。
 *
 * 加载方式说明（Compose Multiplatform 1.11 的桌面端）：
 * - 顶层工厂 `androidx.compose.ui.text.platform.Font` 只剩 `Font(identity)`（按系统字体名）
 *   与 `Font(file)`（按磁盘文件）两个入口，**没有**"按 classpath 资源 / 字节数组"的入口；
 * - 打包字体打进 jar 后不是磁盘文件，所以先把资源落到一个临时文件、再用 `Font(file)` 装载，
 *   与旧代码"从系统字体文件加载"是同一条 `FileFont` 路径，行为确定。
 * - 每次启动首帧才惰性解一次（`deleteOnExit` 自动清理），24MB 的一次性磁盘写可忽略。
 * - 组合回退链要拿 `Font` 对象再传给 `FontFamily(...)`：这个版本的 `FontFamily.fonts` 是 internal，
 *   拿不到已建好的族去"再拼一遍"。
 *
 * 授权：两份字体均以 **SIL OFL 1.1** 随包分发（原文见 `resources/fonts/OFL-*.txt`），
 * 打包的是**未修改的原版**，保留字体名合法。若后续为移动端做子集，需按 OFL 第 3 条
 * 处理保留字体名（霞鹜文楷的 OFL 声明保留「LXGW / 霞鹜」等名）。
 */
private val bundledWenKaiFont: Font? by lazy { loadResourceFont("fonts/LXGWWenKai-Regular.ttf") }

private val bundledNunitoFont: Font? by lazy { loadResourceFont("fonts/Nunito.ttf") }

private fun loadResourceFont(resource: String): Font? {
    val bytes = runCatching {
        Ink::class.java.classLoader.getResourceAsStream(resource)?.use { it.readBytes() }
    }.getOrNull() ?: return null
    return runCatching {
        val file = fontCacheFile(resource)
        // 已缓存且字节数一致就直接复用：字体每次启动都要用，缓存下来省掉约 25MB 的一次性写出。
        if (!file.isFile || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
        PlatformFont(file = file, weight = FontWeight.Normal, style = FontStyle.Normal)
    }.getOrNull()
}

/**
 * 字体缓存位置：`~/.sudoku-ink/fonts/`。
 *
 * 早前用 `File.createTempFile` + `deleteOnExit`：每次启动都要重写约 25MB，且进程被强杀时
 * 临时文件会残留（多开几个实例还会各写一份）。缓存目录里按字节数校验，写一半的坏文件会被重写。
 */
private fun fontCacheFile(resource: String): File {
    val name = resource.substringAfterLast('/')
    val dir = File(System.getProperty("user.home") ?: ".", ".sudoku-ink/fonts")
    dir.mkdirs()
    return File(dir, name)
}

/**
 * 数字族的回退链：**Nunito 优先 + 霞鹜文楷兜底**。
 *
 * 数字与西文走 Nunito；一旦数字样式的位置上出现中文（例如暂停时计时位显示「已暂停」），
 * 由霞鹜文楷接住而不是渲染成豆腐块——`FontFamily` 传多个 `Font` 就是按码点的回退链。
 *
 * 之所以公开：让测试能断言"兜底还在"。若哪天被简化成 Nunito 单字体，
 * 混进中文的地方会静默变豆腐块——这种回归肉眼难以发现。
 */
fun digitFontChain(): List<Font> = listOfNotNull(bundledNunitoFont, bundledWenKaiFont)

private val digitFamily: FontFamily? by lazy {
    val chain = digitFontChain()
    if (chain.isEmpty()) null else FontFamily(*chain.toTypedArray())
}

/** 系统楷书文件（打包字体加载失败时的兜底，按平台路径探测）。 */
private val systemKaiTiFont: Font? by lazy {
    SystemKaiTiFiles.firstNotNullOfOrNull { path ->
        runCatching {
            val file = File(path)
            if (file.isFile) {
                PlatformFont(file = file, weight = FontWeight.Normal, style = FontStyle.Normal)
            } else {
                null
            }
        }.getOrNull()
    }
}

actual fun textFontFamily(): FontFamily? =
    bundledWenKaiFont?.let { FontFamily(it) } ?: systemKaiTiFont?.let { FontFamily(it) }

actual fun digitFontFamily(): FontFamily? = digitFamily

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
