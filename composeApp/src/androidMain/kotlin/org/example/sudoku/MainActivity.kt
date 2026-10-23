package org.example.sudoku

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.example.sudoku.platform.AndroidGameStore
import org.example.sudoku.platform.AppGlobals
import org.example.sudoku.platform.FileSaveMutex
import org.example.sudoku.state.MutexGameStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 刘海 / 挖孔屏：让纸面背景铺满到短边（配合 SafeArea 的 safeDrawing 把内容从缺口让开）。
        // 默认 cutout 模式会把缺口区留黑、且 Compose 的 displayCutout 恒为空，safeDrawing 无从避让——
        // 必须显式设为 shortEdges（API 28+ 才有 cutout 概念，低于 28 无此属性）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // 必须先于 setContent：语料资产与 reduced-motion 判定都在首屏组合期读取
        AppGlobals.init(this)
        val lifecycleOwner = this // MainActivity 即 LifecycleOwner；避免组合期依赖已废弃的 LocalLifecycleOwner
        // 存档实例取**进程级单例**（不能是 Activity 字段，也不能放在 `remember` 里）：
        // 旋转 / 深浅色切换 / 字体缩放都会销毁重建 Activity，Activity 级实例随之重建，
        // 而旧的 `FileLock` 由旧实例握着、重建时并未释放 —— 同一 JVM 对同一文件第二次
        // `tryLock()` 会抛 `OverlappingFileLockException`，新实例抢不到锁 → 静默降级只读 →
        // **旋转一次之后本局所有进度都不再落盘**（且毫无提示）。见 [sharedStore]。
        val gameStore = sharedStore()
        setContent {
            // 窗口活动状态 → App 的 isWindowActive：仅在 RESUMED 时视为活动，
            // 切后台（ON_PAUSE → ON_STOP）或息屏自动暂停计时（与桌面最小化同语义）。
            // 注意不能写 `event != ON_PAUSE`——切后台时 ON_STOP 紧随 ON_PAUSE，
            // 会把 windowActive 又翻回 true，导致"挂后台仍走表"。
            var windowActive by remember { mutableStateOf(true) }
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    windowActive = event == Lifecycle.Event.ON_RESUME
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            // 多实例互斥：同一存档只有一个可写实例（第二个实例降级只读，并由 App 弹一次墨条告知）
            App(
                store = gameStore,
                isWindowActive = windowActive,
            )
        }
    }

    companion object {
        @Volatile
        private var cachedStore: MutexGameStore? = null

        /**
         * 进程级存档单例。
         *
         * **为什么必须是进程级而不是 Activity 级**：`SaveMutex` 用的是 OS 文件锁，而文件锁在
         * **同一 JVM 内是共享的**——第二个 `tryLock()` 不会等待，而是直接抛
         * `OverlappingFileLockException`。因此只要"抢锁"发生两次，第二次必然失败。
         * Activity 在配置变更（旋转、深浅色、字体缩放）时会销毁重建，若把存档建在 Activity
         * 作用域，重建即等于第二次抢锁 → 静默变只读。做成进程级后，抢锁在整个进程生命周期里
         * 只发生一次，重建只是复用同一个实例。
         *
         * 跨**进程**的并发写仍由这把文件锁保护（那才是多实例的真实场景）。
         * 进程退出时由 OS 释放锁，不需要显式 [FileSaveMutex.release]。
         */
        fun sharedStore(): MutexGameStore =
            cachedStore ?: synchronized(this) {
                cachedStore ?: run {
                    val base = AndroidGameStore()
                    MutexGameStore(base, FileSaveMutex(base.saveFile)).also { cachedStore = it }
                }
            }
    }
}
