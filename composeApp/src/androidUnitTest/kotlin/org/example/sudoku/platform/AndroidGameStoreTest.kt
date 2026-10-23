package org.example.sudoku.platform

import org.example.sudoku.state.SaveFile
import org.example.sudoku.state.GameSettings
import org.example.sudoku.state.GameStats
import org.example.sudoku.state.KeyMap
import org.example.sudoku.state.AppLanguage
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `AndroidGameStore` 的行为测试（跑在纯 JVM 上，见 `build.gradle.kts` 的 `androidUnitTest` 注释）。
 *
 * **为什么值得单独测**：存档是"数据丢失"类缺陷的高发地，而 `AndroidGameStore` 与
 * `FileGameStore` 的**原子写**逻辑此前没有任何直接测试——`GameStoreTest` 测的是编解码，
 * 落盘实现一直是空白。这里覆盖它真正容易错的地方：
 * 1. 覆盖写（第二次保存必须整体替换，不能残留旧内容）；
 * 2. **临时文件不残留**（`save.txt.tmp` 写完必须已被 move 掉，否则目录里越攒越多）；
 * 3. 目录不存在时自动创建（首次启动就是这种情形）；
 * 4. 写出 → 读回**往返一致**（编码器与解码器的接口契约）。
 *
 * 不测 `defaultSaveFile()`：它要 `AppGlobals.appContext`（真 Android Context），
 * 而构造器允许注入 `saveFile`，测它没有收益。
 */
class AndroidGameStoreTest {

    private lateinit var dir: File
    private lateinit var saveFile: File

    @BeforeTest
    fun setUp() {
        dir = File.createTempFile("android-store-", "-dir").let { f ->
            f.delete()
            f.mkdirs()
            f
        }
        saveFile = File(dir, "save.txt")
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun sampleSave(eraseKey: String = "0"): SaveFile = SaveFile(
        settings = GameSettings(
            strictMode = true,
            showNotes = true,
            noteMode = false,
            hintCandidates = false,
            darkMode = true,
            keyMap = KeyMap.DEFAULT.copy(erase = eraseKey),
            language = AppLanguage.En,
        ),
        stats = GameStats(gamesWon = 3, gamesAbandoned = 1),
        game = null,
    )

    @Test
    fun savesAndLoadsRoundTrip() {
        val store = AndroidGameStore(saveFile)
        store.save(sampleSave())

        val loaded = store.load()
        assertNotNull(loaded, "保存后应能读回")
        assertTrue(loaded.settings.strictMode, "严格模式应往返一致")
        assertTrue(loaded.settings.darkMode, "夜墨模式应往返一致")
        assertEquals(AppLanguage.En, loaded.settings.language, "语言应往返一致")
        assertEquals(3, loaded.stats.gamesWon, "胜场应往返一致")
    }

    @Test
    fun secondSaveReplacesContentEntirely() {
        val store = AndroidGameStore(saveFile)
        store.save(sampleSave(eraseKey = "0"))
        store.save(sampleSave(eraseKey = "Backspace"))

        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals("Backspace", loaded.settings.keyMap.erase, "第二次保存应整体替换，而不是与旧内容拼接")
    }

    /**
     * 临时文件必须**不残留**：`save()` 用的是"写 tmp → ATOMIC_MOVE 覆盖"，
     * 若 move 失败或忘记 move，目录里会留一份 `save.txt.tmp`。
     * 这类残留不会被功能发现，但会随每次保存累积（同一路径反复覆盖其实只有一份，
     * 真正危险的是"move 失败却当作成功"——那种情况下存档是旧的而 tmp 是新的）。
     */
    @Test
    fun temporaryFileIsNotLeftBehind() {
        val store = AndroidGameStore(saveFile)
        store.save(sampleSave())

        val leftovers = dir.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue(leftovers.isEmpty(), "保存后不应残留临时文件，实际: ${leftovers.map { it.name }}")
    }

    @Test
    fun createsMissingDirectory() {
        // 首次启动时 filesDir 可能还不存在（或刚被清理）
        val nested = File(File(dir, "not-yet"), "deeper/save.txt")
        assertFalse(nested.parentFile!!.isDirectory, "前置条件：目录应不存在")

        AndroidGameStore(nested).save(sampleSave())
        assertTrue(nested.isFile, "父目录不存在时应自动创建并写入")
    }

    @Test
    fun loadReturnsNullWhenFileMissing() {
        assertNull(AndroidGameStore(saveFile).load(), "存档不存在时应返回 null 而不是抛异常")
    }

    /**
     * 存档是**外部输入**：内容损坏时 `load()` 必须返回 null（走默认值），
     * 而不是把异常抛给调用方——启动期抛异常等于起不来。
     */
    @Test
    fun loadReturnsNullOnCorruptContent() {
        saveFile.writeText("this is not a valid save file\nv=notanumber\n")
        assertNull(AndroidGameStore(saveFile).load(), "损坏的存档应降级为 null，不能抛异常")
    }

    /**
     * 写入失败不应影响调用方（`save()` 内部把所有 IO 包在 `runCatching` 里）。
     *
     * 用"父路径是个文件"来制造必然失败：`save.txt` 的上级 `blocked` 已被一个普通文件占据，
     * 无法在其下创建目录 / 文件。
     */
    @Test
    fun saveFailureDoesNotThrow() {
        val blocked = File(dir, "blocked").apply { writeText("i am a file, not a directory") }
        val impossible = File(blocked, "save.txt")
        AndroidGameStore(impossible).save(sampleSave()) // 不应抛异常
        assertFalse(impossible.exists(), "前置条件核对：目标确实写不进去")
    }
}
