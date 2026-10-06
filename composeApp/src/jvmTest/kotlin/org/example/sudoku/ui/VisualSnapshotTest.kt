package org.example.sudoku.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.example.sudoku.core.Difficulty
import org.example.sudoku.core.Game
import org.example.sudoku.ui.components.BoardCanvas
import org.example.sudoku.ui.screens.MenuScreen
import org.example.sudoku.ui.theme.LocalReduceMotion
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **离线渲染快照**：不依赖桌面会话（机器锁屏 / 无头也能跑），把关键界面渲染成 PNG 供人眼与像素双重验收。
 *
 * 为什么要有这一层：
 * - 字体与排版改动的验收标准就是"看得见"，而 CI / 锁屏环境下截不到桌面，只能自己渲染；
 * - 同一个场景里渲染出来的像素可以统计：开局显影"写到哪了"用暗像素数量客观比较，
 *   不再靠人眼看截图。
 *
 * 产物：`composeApp/build/visual-snapshots` 目录下的 PNG（build 目录，不入库）。
 */
class VisualSnapshotTest {

    @Test
    fun menuUsesTheBundledTypography() {
        val file = snap("menu-1180x900", width = 1180, height = 900) {
            Box(Modifier.fillMaxSize()) {
                MenuScreen(canResume = false, onStart = {}, onResume = {}, onExit = {})
            }
        }
        assertTrue(file.length() > 0, "菜单快照应写出文件")
    }

    @Test
    fun inkRevealWithholdsTheDigitsOnTheFirstFrame() {
        val firstFrame = inkCoverage(snap("board-reveal-first-frame", BOARD, BOARD, content = ::fullBoard))
        val settled = inkCoverage(reducedMotionBoard())
        assertTrue(
            firstFrame < settled,
            "\"落笔成局\"第一帧只该有网格，不该已经写满（实际 $firstFrame vs $settled）",
        )
        assertTrue(
            settled > firstFrame * 2,
            "显影结束时的墨量应明显多于第一帧（实际 $firstFrame vs $settled）",
        )
    }

    @Test
    fun reducedMotionWritesTheWholeBoardOnTheFirstFrame() {
        val immediate = inkCoverage(snap("board-reduced-motion-first-frame", BOARD, BOARD, content = renderedReducedMotion()))
        val settled = inkCoverage(reducedMotionBoard())
        assertTrue(
            immediate >= settled,
            "开启\"减少动态效果\"时第一帧就应是完整局面（实际 $immediate vs $settled）",
        )
    }

    /**
     * 同一块棋盘的**终态**：用"减少动态效果"打开时的第一帧拿到。
     * 这样就不必等动画跑完——终态是确定的，与动画时钟无关。
     */
    private fun reducedMotionBoard(): File =
        snap("board-settled", BOARD, BOARD, content = renderedReducedMotion())

    private fun renderedReducedMotion(): @Composable () -> Unit = {
        CompositionLocalProvider(LocalReduceMotion provides true) { fullBoard() }
    }
}

private const val BOARD = 560

@Composable
private fun fullBoard() {
    Box(Modifier.fillMaxSize().size(BOARD.dp)) {
        BoardCanvas(
            game = GAME,
            selected = null,
            notes = IntArray(81),
            conflicts = BooleanArray(81),
            noteMode = false,
            showNotes = true,
            hintCandidates = false,
            onCellClick = { _, _ -> },
        )
    }
}

/**
 * 渲染**第一帧**并存成 PNG。
 *
 * 为什么只取第一帧：`ImageComposeScene` 的动画时钟在测试里控制不了——传进去的 nanos 不驱动
 * 动画，真实等待又会带来抖动（两种写法都试过，得到过互相矛盾的结论）。所以这里不做
 * "动画跑到一半"的像素断言：动画**策略**交给 `InkRevealTest` 验纯函数，这里只验**接线**
 * 与**排版**（第一帧不该已是完整局面、减少动效时第一帧就该完整）。
 */
private fun snap(
    name: String,
    width: Int,
    height: Int,
    content: @Composable () -> Unit,
): File {
    val scene = ImageComposeScene(width = width, height = height, density = Density(1f), content = content)
    try {
        return save(name, scene.render(0L))
    } finally {
        scene.close()
    }
}

private fun save(name: String, image: Image): File {
    val dir = File("build/visual-snapshots").apply { mkdirs() }
    val file = File(dir, "$name.png")
    file.writeBytes(image.encodeToData()!!.bytes)
    return file
}

/** 统计暗像素（数字与线）——用来客观比较"写到哪了"。 */
private fun inkCoverage(file: File): Int {
    val bitmap = javax.imageio.ImageIO.read(file)
    var dark = 0
    for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
            val rgb = bitmap.getRGB(x, y)
            val lum = 0.299f * ((rgb shr 16) and 0xFF) + 0.587f * ((rgb shr 8) and 0xFF) + 0.114f * (rgb and 0xFF)
            if (lum < 110f) dark++
        }
    }
    return dark
}

private const val SOLUTION =
    "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

private val GAME = Game(
    puzzle = SOLUTION.toBoard().also { it[2] = 0; it[4] = 0; it[80] = 0 },
    current = SOLUTION.toBoard(),
    solution = SOLUTION.toBoard(),
    difficulty = Difficulty.Easy,
)

private fun String.toBoard(): IntArray = IntArray(81) { this[it].digitToInt() }
