package org.example.sudoku.platform

import android.content.Context

/**
 * Android 端的全局 Context 锚点：**在 [org.example.sudoku.MainActivity] 的 `onCreate` 最前面初始化**，
 * 供没有现成 Context 参数的平台接缝使用（语料资产读取、reduced-motion 系统设置）。
 *
 * 为什么不用自定义 Application：接缝们只在首屏组合期读取，`MainActivity.onCreate` 先于
 * `setContent` 执行即可保证时序；少一个类就少一处清单配置。
 */
object AppGlobals {
    var appContext: Context? = null
        private set

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }
}
