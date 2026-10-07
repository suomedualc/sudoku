package org.example.sudoku.platform

import org.example.sudoku.state.GameStore
import org.example.sudoku.state.SaveCodec
import org.example.sudoku.state.SaveFile
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Android 端存档实现：写到应用私有目录（`filesDir/save.txt`），无需任何权限。
 *
 * 与桌面端 [org.example.sudoku.platform.FileGameStore] 同一条纪律：
 * - **原子写**：先写同目录临时文件，再 ATOMIC_MOVE 覆盖——进程被杀 / 磁盘满不会剩半份存档；
 * - 所有 IO 包在 `runCatching` 里：存档失败不应该影响玩游戏。
 *
 * 路径来自 [AppGlobals]（须在 MainActivity.onCreate 初始化，早于首次组合）。
 */
class AndroidGameStore(private val saveFile: File = defaultSaveFile()) : GameStore {

    override fun load(): SaveFile? = runCatching {
        if (saveFile.isFile) SaveCodec.decode(saveFile.readText()) else null
    }.getOrNull()

    override fun save(file: SaveFile) {
        runCatching {
            val dir = saveFile.parentFile
            if (dir != null && !dir.isDirectory) dir.mkdirs()
            val text = SaveCodec.encode(file)

            val tmp = File(dir ?: File("."), "${saveFile.name}.tmp")
            tmp.writeText(text)
            try {
                Files.move(
                    tmp.toPath(),
                    saveFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                // 少数文件系统不支持原子移动：退化为普通覆盖（内容已完整写在临时文件里）
                Files.move(tmp.toPath(), saveFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    companion object {
        fun defaultSaveFile(): File {
            val context = AppGlobals.appContext
                ?: throw IllegalStateException("AppGlobals 未初始化：须在 MainActivity.onCreate 先调 AppGlobals.init()")
            return File(context.filesDir, "save.txt")
        }
    }
}
