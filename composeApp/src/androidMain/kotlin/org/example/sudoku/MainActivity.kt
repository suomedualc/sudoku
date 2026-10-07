package org.example.sudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.example.sudoku.platform.AndroidGameStore
import org.example.sudoku.platform.AppGlobals

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 必须先于 setContent：语料资产与 reduced-motion 判定都在首屏组合期读取
        AppGlobals.init(this)
        setContent {
            // 窗口活动状态 → App 的 isWindowActive：切后台 / 息屏自动暂停计时（与桌面最小化同语义）
            val lifecycleOwner = LocalLifecycleOwner.current
            var windowActive by remember { mutableStateOf(true) }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    windowActive = event != Lifecycle.Event.ON_PAUSE
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            App(store = remember { AndroidGameStore() }, isWindowActive = windowActive)
        }
    }
}
