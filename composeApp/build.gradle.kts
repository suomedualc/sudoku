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
    }
}

// Android 端配置：命名空间即 R 类包；业务入口仍是 commonMain 的 ui.App
android {
    namespace = "org.example.sudoku"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.example.sudoku"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
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
