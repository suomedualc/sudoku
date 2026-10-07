package org.example.sudoku.platform

import org.example.sudoku.state.GameStore
import org.example.sudoku.state.SaveCodec
import org.example.sudoku.state.SaveFile
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 桌面端存档实现：写到用户目录下的单文件，无需任何第三方依赖。
 *
 * 位置：`~/.sudoku-ink/save.txt`（可用 [saveFile] 参数覆盖，便于测试）。
 * 文件内容见 [SaveCodec] v2：设置常驻，未完成对局可选。
 * 所有 IO 都包在 `runCatching` 里：存档失败不应该影响玩游戏。
 */
class FileGameStore(private val saveFile: File = defaultSaveFile()) : GameStore {

    override fun load(): SaveFile? = runCatching {
        if (saveFile.isFile) SaveCodec.decode(saveFile.readText()) else null
    }.getOrNull()

    override fun save(file: SaveFile) {
        runCatching {
            val dir = saveFile.parentFile
            if (dir != null && !dir.isDirectory) dir.mkdirs()
            val text = SaveCodec.encode(file)

            // 原子写：先写同目录临时文件，再 ATOMIC_MOVE 覆盖。
            // 直接 writeText 会在"截断旧文件 → 写完新内容"之间留下一个窗口：
            // 此刻进程被杀 / 磁盘写满，存档就只剩半份，下次启动 decode 失败 →
            // 对局与偏好一起作废。临时文件必须**同目录**，否则跨文件系统的 move 不保证原子。
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
                // 少数文件系统 / 网络盘不支持原子移动：退化为普通覆盖（内容已完整写在临时文件里）
                Files.move(tmp.toPath(), saveFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    companion object {
        fun defaultSaveFile(): File =
            File(System.getProperty("user.home") ?: ".", ".sudoku-ink/save.txt")
    }
}
