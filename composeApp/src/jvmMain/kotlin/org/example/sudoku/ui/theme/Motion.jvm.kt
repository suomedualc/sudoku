package org.example.sudoku.ui.theme

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 桌面端的"减少动态效果"判定。
 *
 * 优先顺序：显式覆盖 → 系统设置 → 关闭动效为 false（保持动画）。
 * 系统设置只能靠**查询本机的无障碍/动效开关**获得：JRE 没有暴露这项（AWT 只暴露了字体与颜色等
 * 桌面属性），而引入 JNA 需要联网拉依赖，因此这里走"调用系统自带命令行并限时读取"——
 * 所有调用都有超时与异常兜底，**查不到就当"不减少"**，绝不会因为探测失败而卡住或改坏体验。
 *
 * 覆盖方式（验收 / 截图对比 / 用户偏好都能用）：
 * - JVM 参数：`-Dsudoku.reduceMotion=true`
 * - 环境变量：`SUDOKU_REDUCE_MOTION=true`
 */
actual fun prefersReducedMotion(): Boolean = DesktopReduceMotion.value

private object DesktopReduceMotion {

    /** 惰性：第一次用到动效时才探测，不拖慢冷启动；探测结果缓存（改系统设置需重启应用）。 */
    val value: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { detect() }

    private fun detect(): Boolean {
        overrideFromProperty()?.let { return it }
        System.getenv(ENV_OVERRIDE)?.toBooleanOrNull()?.let { return it }
        return when {
            isWindows() -> windowsReducedMotion() ?: false
            isMac() -> commandMatches("defaults", "read", "com.apple.universalaccess", "reduceMotion") { out ->
                out.trim().endsWith("1")
            } ?: false
            else -> commandMatches("gsettings", "get", "org.gnome.desktop.interface", "enable-animations") { out ->
                out.trim().equals("false", ignoreCase = true)
            } ?: false
        }
    }

    private fun overrideFromProperty(): Boolean? = System.getProperty(PROP_OVERRIDE)?.toBooleanOrNull()

    private fun isWindows(): Boolean = osName().contains("win")

    private fun isMac(): Boolean = osName().contains("mac")

    /**
     * Windows：「性能选项 → 调整为最佳性能」会把 `VisualFXSetting` 置为 2，
     * 对应的就是"关闭所有不必要的动画"。0 = 让 Windows 选择、1 = 最佳外观、3 = 自定义。
     */
    private fun windowsReducedMotion(): Boolean? = commandMatches(
        "reg",
        "query",
        VISUAL_FX_KEY,
        "/v",
        "VisualFXSetting",
    ) { out ->
        // 输出形如：`    VisualFXSetting    REG_DWORD    0x2`
        Regex("VisualFXSetting\\s+REG_DWORD\\s+0x2\\b").containsMatchIn(out)
    }

    /** 执行系统命令并限时读取；任何失败（命令不存在 / 被拦截 / 超时）都返回 null。 */
    private fun commandMatches(vararg args: String, matches: (String) -> Boolean): Boolean? {
        return try {
            val process = ProcessBuilder(args.toList())
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            val output = process.inputStream.bufferedReader().readText()
            if (process.exitValue() != 0) null else matches(output)
        } catch (_: Throwable) {
            null
        }
    }

    private fun String.toBooleanOrNull(): Boolean? = when (trim().lowercase(Locale.ROOT)) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> null
    }

    private fun osName(): String = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)

    private const val PROP_OVERRIDE = "sudoku.reduceMotion"
    private const val ENV_OVERRIDE = "SUDOKU_REDUCE_MOTION"
    private const val VISUAL_FX_KEY =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VisualEffects"
    private const val COMMAND_TIMEOUT_MS = 800L
}
