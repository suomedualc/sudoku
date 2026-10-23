// compose.uiTest 目前标记为实验性，需要显式 opt-in（仅用于 jvmTest 的 UI 测试依赖）
@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // 顺序有讲究：KGP 在应用时必须能找到已存在的 AGP，androidApplication 必须在最前。
    // AGP 版本由根 build.gradle.kts 的 buildscript 类路径提供（见其注释），此处不带 version。
    id("com.android.application")
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // 固定编译工具链：Gradle 8.12 可用，Kotlin 2.4.0 与 Compose Multiplatform 1.11.1 对齐。
    // 注意：这只是编译/测试用的 JDK；启动 Gradle 的 JAVA_HOME 同样必须指向 JDK 17+（≤24），
    //       实测 JDK 25 会让 Gradle 8.12 直接失败（详见 docs/03-开发流程.md §1）。
    jvmToolchain(21)

    // Android（docs/05 P0）：业务代码零改动，只在此处与 androidMain 的平台接缝落地。
    androidTarget {
        compilerOptions {
            // DEX 工具链最稳的字节码档位；桌面端仍为 21
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime.ktx)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain {
            // 把生成的应用图标（tools/make-icon.ps1 的产物）打进 jar：
            // jpackage 只把图标**嵌入 exe**，AWT 窗口默认仍显示 JDK 的 Java 图标，
            // 所以运行时要在 main.kt 里用这些资源设置 window.iconImages（与打包图标同源）。
            // .ico / .icns 只供打包用，不进 jar（各平台图标由 jpackage 读取）。
            resources.srcDir("icons")
            resources.exclude("**/*.ico", "**/*.icns")
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }

        jvmTest.dependencies {
            // UI 测试：抽屉 / 覆盖层的**键盘路由**只有在真实组合里才能验（父先于子的
            // onPreviewKeyEvent 顺序、焦点归属、Esc 归属），因此这里引入 Compose 测试框架。
            implementation(compose.uiTest)
            implementation(compose.desktop.currentOs)
        }

        // Android 端单元测试（`gradlew :composeApp:testDebugUnitTest`）。
        //
        // **为什么能在纯 JVM 上跑**：`AndroidGameStore` 只依赖 `java.io` / `java.nio`
        // （原子写用 `Files.move(ATOMIC_MOVE)`，API 26+ 才有，这也是 minSdk=26 的原因），
        // **不碰任何 `android.*` API**——唯一与 Android 相关的是 `defaultSaveFile()` 里的
        // `AppGlobals.appContext`，而构造器允许显式传入 `saveFile`，测试绕开它即可。
        // 因此这里不需要 Robolectric（省掉一个重依赖 + 一整套 Android 资源模拟）。
        //
        // 覆盖目标：原子写的**实际行为**（覆盖写、临时文件不残留、目录自动创建、
        // 写出与读回往返一致）。这些此前**两个实现都没有直接测试**，
        // 而存档路径正是"数据丢失"类缺陷的所在地。
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Android 端配置：命名空间即 R 类包；业务入口仍是 commonMain 的 ui.App
android {
    namespace = "org.example.sudoku"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.example.sudoku"
        // Android 8.0+（API 26）：存档用到的 java.nio.file.Files.move 需要 API 26；
        // 也贴合本项目"8.0 及以上"的兼容承诺（见 docs/08 验收口径）。
        minSdk = 26
        targetSdk = 36
        // 版本号约定：**versionCode 由 versionName 推导**，major*10000 + minor*100 + patch。
        //   versionName 2.1.0 → versionCode 20100
        // 为什么不再用 1：versionCode 是 Play 判定"新旧"的唯一依据，与 versionName 无关。
        // 用 1 起步会得到一个"语义是 2.1.0、排序是第 1 版"的割裂状态——后续任何更新都必须
        // 手动跳到比 1 大的数，而开发者在 versionName 上看到的进度（2.1 → 2.2）与
        // versionCode 完全对不上。按上面的公式推导，两者永远同向，且留足每段 100 个补丁位。
        // 递增规则：改 versionName 必须同步改 versionCode（本注释就是唯一的约定来源）。
        versionCode = 20100
        versionName = "2.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "org.example.sudoku.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // 打包标识必须纯 ASCII、无空格：jpackage 会把它用作产品名 / 安装目录名 / MSI 标识，
            // 中文与空格在 MSI、DMG 上会产生异常产品名甚至直接失败（旧值 "数独 Sudoku"）。
            packageName = "SudokuInk"
            packageVersion = "2.0.0"
            description = "Sudoku with hand-drawn ink style (Kotlin + Compose Multiplatform)"
            vendor = "suomedualc"
            copyright = "Copyright (c) 2026 suomedualc. MIT License."
            // 图标由 tools/make-icon.ps1 生成（纸面 + 九宫格数字），三平台各取所需；
            // .icns 由脚本直接写出（11 个 PNG 块），同时给出 iconutil 用的 .iconset 供 macOS 上复核。
            windows { iconFile.set(project.file("icons/sudoku.ico")) }
            linux { iconFile.set(project.file("icons/sudoku.png")) }
            macOS { iconFile.set(project.file("icons/SudokuInk.icns")) }
        }
    }
}
