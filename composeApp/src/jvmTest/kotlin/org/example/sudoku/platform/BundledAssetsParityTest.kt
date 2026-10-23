package org.example.sudoku.platform

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 跨源集资产**一致性**守卫：`jvmMain/resources` 与 `androidMain` 是两份**物理副本**。
 *
 * 为什么需要这个测试：字体（霞鹜文楷 25.6MB）与语料库在两端各存一份，内容必须逐字节相同，
 * 但**没有任何构建期约束**保证这一点——`jvmMain/resources/fonts/X.ttf` 与
 * `androidMain/res/font/x.ttf` 是两个不同路径、不同文件名，改字体时很容易只换一处。
 * 这种漂移不会让任何一边编译失败，只会让"桌面端显示的是新字体、Android 还是旧字体"，
 * 极难在测试里偶然发现。
 *
 * 这里用 **SHA-256 逐字节比对**（不是"文件存在"或"大小接近"）：字体文件同名同大小
 * 但内容不同是完全可能的（不同子集化参数就会这样）。
 *
 * 只在 `jvmTest` 里跑（Android 源集的路径要从仓库根解析），因此不放进 `commonTest`。
 */
class BundledAssetsParityTest {

    /**
     * 仓库根目录。
     *
     * `jvmTest` 的工作目录是 `composeApp/`（Gradle 的默认测试工作目录是子项目目录），
     * 因此上一级就是仓库根。找不到就**跳过而不是失败**：这个测试的价值在于"本仓库内"
     * 的一致性，脱离仓库结构（如从 jar 里跑）时不该误报。
     */
    private val repoRoot: File? = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "composeApp/src/jvmMain").isDirectory }

    @Test
    fun fontCopiesAreByteIdentical() {
        assertParity(
            left = "composeApp/src/jvmMain/resources/fonts/LXGWWenKai-Regular.ttf",
            right = "composeApp/src/androidMain/res/font/lxgw_wen_kai.ttf",
            what = "霞鹜文楷字体",
        )
    }

    @Test
    fun digitFontCopiesAreByteIdentical() {
        assertParity(
            left = "composeApp/src/jvmMain/resources/fonts/Nunito.ttf",
            right = "composeApp/src/androidMain/res/font/nunito.ttf",
            what = "Nunito 数字字体",
        )
    }

    @Test
    fun sentenceCorpusCopiesAreByteIdentical() {
        assertParity(
            left = "composeApp/src/jvmMain/resources/corpus/sentences.txt",
            right = "composeApp/src/androidMain/assets/corpus/sentences.txt",
            what = "随机句子语料库",
        )
    }

    /**
     * 两端各有哪些字体资产必须**成对存在**。
     *
     * 防止"只在一边新增了字体"这种漂移：比如给桌面加了粗体、忘了 Android 那份，
     * 结果是桌面好看、Android 悄悄回退到系统字体。
     */
    @Test
    fun fontAssetSetsMatch() {
        val root = repoRoot ?: return
        val jvmFonts = File(root, "composeApp/src/jvmMain/resources/fonts")
            .listFiles { f -> f.extension.equals("ttf", true) }
            ?.map { it.name.lowercase() }
            ?.toSet()
            ?: return
        val androidFonts = File(root, "composeApp/src/androidMain/res/font")
            .listFiles { f -> f.extension.equals("ttf", true) }
            ?.map { it.name.lowercase() }
            ?.toSet()
            ?: return

        assertEquals(
            jvmFonts.size,
            androidFonts.size,
            "两端 TTF 数量应一致（jvm=$jvmFonts android=$androidFonts）——" +
                "新增字体时两边都要放，否则会悄悄回退到系统字体",
        )
    }

    private fun assertParity(left: String, right: String, what: String) {
        val root = repoRoot ?: return
        val a = File(root, left)
        val b = File(root, right)
        assertTrue(a.isFile, "$what 缺失：$left")
        assertTrue(b.isFile, "$what 缺失：$right")
        // 用 SHA-256 而不是 contentHashCode()：后者只有 32 位，拿来做"逐字节相同"的判据
        // 在 25MB 字体上碰撞概率虽低但并非为零，而误判成"一致"正是本测试要防的事。
        assertEquals(
            sha256(a) to a.length(),
            sha256(b) to b.length(),
            "$what 两端副本内容不一致——改了字体/语料必须两边同步（$left ↔ $right）",
        )
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
