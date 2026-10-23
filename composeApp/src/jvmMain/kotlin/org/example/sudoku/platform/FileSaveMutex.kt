package org.example.sudoku.platform

import org.example.sudoku.state.SaveMutex
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * 基于 `FileChannel.tryLock` 的进程级排他写锁：对存档文件旁的 `.lock` 文件加锁。
 *
 * - **跨进程**：第二个实例 `tryLock()` 返回 null → 抢锁失败，降级只读；
 * - **同进程**：重复加锁抛 `OverlappingFileLockException` → 同样按"抢不到"处理。
 *
 * 锁在进程存活期间持有（不主动释放），进程退出时由 OS 自动回收；
 * `.lock` 文件零字节、与存档同名相邻、常驻，且**不参与 ATOMIC_MOVE**
 * （存档替换的是 `save.txt` 的 inode，锁在 `.lock` 文件上互不影响）。
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