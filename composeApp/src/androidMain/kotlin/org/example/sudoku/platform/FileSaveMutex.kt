package org.example.sudoku.platform

import org.example.sudoku.state.SaveMutex
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * Android 端的进程级排他写锁（与桌面 [FileSaveMutex] 同一套实现）：
 * 对存档文件旁的 `.lock` 文件 `tryLock`，`RandomAccessFile` + `FileChannel` 均为 API 1+ 可用。
 *
 * 多实例场景在 Android 上较罕见（默认单 Activity），但进程被拉起多份、或
 * 备份 / 恢复时序下仍有并发写风险，靠这层互斥保证存档不被并发覆盖。
 */
class FileSaveMutex(saveFile: File) : SaveMutex {
    private val lockFile = File(saveFile.parentFile, "${saveFile.name}.lock")
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    override fun acquire(): Boolean {
        if (lock?.isValid == true) return true
        val dir = lockFile.parentFile
        if (dir != null && !dir.isDirectory) dir.mkdirs()

        val ch = runCatching { RandomAccessFile(lockFile, "rw").channel }.getOrNull() ?: return false
        val acquired = runCatching { ch.tryLock() }.getOrNull()
        if (acquired == null) {
            runCatching { ch.close() }
            return false
        }
        channel = ch
        lock = acquired
        return true
    }

    override fun release() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }
}