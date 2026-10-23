package org.example.sudoku.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 多实例互斥策略的单元测试：核心逻辑（抢到锁可写、抢不到只读）与平台文件锁解耦，
 * 用内存 [SaveMutex] / [GameStore] 精确模拟，不碰磁盘。
 */
class MutexGameStoreTest {

    @Test
    fun writesThroughWhenLockAcquired() {
        val delegate = CountedDelegate()
        val store = MutexGameStore(delegate, ScriptedMutex(granted = true))

        assertTrue(store.writable, "抢到锁的实例应可写")

        store.save(SaveFile())
        assertEquals(1, delegate.saveCount, "持有锁时 save 应透传给底层存档")

        store.load()
        assertEquals(1, delegate.loadCount, "load 无论是否可写都应透传")
    }

    @Test
    fun dropsWritesWhenLockDenied() {
        val delegate = CountedDelegate()
        val store = MutexGameStore(delegate, ScriptedMutex(granted = false))

        assertFalse(store.writable, "抢不到锁的实例应只读")

        store.save(SaveFile())
        assertEquals(0, delegate.saveCount, "抢不到锁时 save 应静默跳过，避免覆盖主实例数据")

        store.load()
        assertEquals(1, delegate.loadCount, "只读实例仍可 load（看盘面）")
    }
}

/** 可编程写锁：直接指定"抢到 / 抢不到"。 */
private class ScriptedMutex(private val granted: Boolean) : SaveMutex {
    override fun acquire(): Boolean = granted
    override fun release() = Unit
}

/** 只计数的存档：断言"什么操作触达了底层"。 */
private class CountedDelegate : GameStore {
    var saveCount = 0
        private set
    var loadCount = 0
        private set

    override fun load(): SaveFile? {
        loadCount++
        return null
    }

    override fun save(file: SaveFile) {
        saveCount++
    }
}