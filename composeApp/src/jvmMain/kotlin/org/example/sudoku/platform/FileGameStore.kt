package org.example.sudoku.platform

import org.example.sudoku.state.GameStore
import org.example.sudoku.state.SaveCodec
import org.example.sudoku.state.SavedGame
import java.io.File

/**
 * 桌面端存档实现：写到用户目录下的单文件，无需任何第三方依赖。
 *
 * 位置：`~/.sudoku-ink/save.txt`（可用 [file] 参数覆盖，便于测试）。
 * 所有 IO 都包在 `runCatching` 里：存档失败不应该影响玩游戏。
 */
class FileGameStore(private val file: File = defaultSaveFile()) : GameStore {

    override fun load(): SavedGame? = runCatching {
        if (file.isFile) SaveCodec.decode(file.readText()) else null
    }.getOrNull()

    override fun save(saved: SavedGame) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(SaveCodec.encode(saved))
        }
    }

    override fun clear() {
        runCatching { if (file.exists()) file.delete() }
    }

    companion object {
        fun defaultSaveFile(): File =
            File(System.getProperty("user.home") ?: ".", ".sudoku-ink/save.txt")
    }
}
