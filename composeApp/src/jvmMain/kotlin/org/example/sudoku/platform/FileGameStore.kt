package org.example.sudoku.platform

import org.example.sudoku.state.GameStore
import org.example.sudoku.state.SaveCodec
import org.example.sudoku.state.SaveFile
import java.io.File

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
            saveFile.parentFile?.mkdirs()
            saveFile.writeText(SaveCodec.encode(file))
        }
    }

    companion object {
        fun defaultSaveFile(): File =
            File(System.getProperty("user.home") ?: ".", ".sudoku-ink/save.txt")
    }
}
