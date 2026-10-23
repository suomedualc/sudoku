package org.example.sudoku.platform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 平台文件锁的实机行为测试（JVM）：验证 [FileSaveMutex] 的跨实例互斥语义
 * —— 同一 JVM 内用两个实例、两个 FileChannel 对同一 `.lock` 文件加锁，
 * 第二个实例的 `tryLock` 抛 `OverlappingFileLockException`，[acquire] 应返回 false。
 */
class FileSaveMutexTest {

    @Test
    fun secondInstanceCannotAcquireWhileFirstHolds() {
        val dir = File.createTempFile("sudoku-mutex-", ".dir").let { f ->
            f.delete()
            f.mkdirs()
            f
        }
        try {
            val saveFile = File(dir, "save.txt")
            val first = FileSaveMutex(saveFile)
            val second = FileSaveMutex(saveFile)

            assertTrue(first.acquire(), "第一个实例应抢到锁")
            assertFalse(second.acquire(), "第二个实例不应抢到锁（降级只读）")

            first.release()
            assertTrue(second.acquire(), "主实例释放后，第二个实例应能抢到锁")
            second.release()
        } finally {
            dir.deleteRecursively()
        }
    }
}