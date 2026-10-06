package org.example.sudoku

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.example.sudoku.platform.FileGameStore
import org.example.sudoku.ui.theme.DesignTokens
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

fun main() = application {
    val windowState = rememberWindowState(size = DesignTokens.Sizes.WindowDefault)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "数独 Sudoku",
        // 创建时就给一个图标：否则启动瞬间会闪出 JDK 默认的 Java 图标（多尺寸列表在下面补齐）
        icon = WindowIcons.lastOrNull()?.let { BitmapPainter(it.toComposeImageBitmap()) },
    ) {
        LaunchedEffect(Unit) {
            // Compose Desktop 未提供最小尺寸 API，用底层 AWT 设置（与 docs/02-设计规范.md §7 一致）
            window.minimumSize = Dimension(940, 720)

            // 窗口 / 任务栏图标：jpackage 只把图标**嵌进 exe**，AWT 窗口默认仍用 JDK 的 Java 图标，
            // 因此必须显式设置。多尺寸列表能让任务栏在大图标模式下也不发虚。
            if (WindowIcons.isNotEmpty()) window.iconImages = WindowIcons
        }

        // 桌面注入文件存档：实现首页「继续游戏」与关窗后恢复；
        // isWindowActive 用于最小化时自动暂停（避免挂后台还在走表）
        val store = remember { FileGameStore() }
        App(
            store = store,
            onExit = ::exitApplication,
            isWindowActive = !windowState.isMinimized,
        )
    }
}

/** 仅用于取得本模块的类加载器（资源查找的锚点）。 */
private val IconAnchor = object {}

/**
 * 运行时可用的窗口图标（多尺寸，小到大）。
 *
 * 资源由 `tools/make-icon.ps1` 生成到 `composeApp/icons/`，再经 `build.gradle.kts` 的
 * `jvmMain { resources.srcDir("icons") }` 打进 jar —— **与打包用的 `.ico` 同源**，改图标只需重跑脚本。
 * 读不到资源时返回空列表：宁可不设图标，也不能因为缺资源起不来。
 */
private val WindowIcons: List<BufferedImage> by lazy {
    WindowIconResources.mapNotNull { name ->
        // 注意用 Class.getResourceAsStream（支持 "/" 开头），ClassLoader 的那个不支持前导斜杠
        IconAnchor.javaClass.getResourceAsStream(name)?.use { stream ->
            runCatching { ImageIO.read(stream) }.getOrNull()
        }
    }
}

private val WindowIconResources = listOf(
    "/sudoku-16.png",
    "/sudoku-24.png",
    "/sudoku-32.png",
    "/sudoku-48.png",
    "/sudoku-64.png",
    "/sudoku-128.png",
    "/sudoku.png",
)
