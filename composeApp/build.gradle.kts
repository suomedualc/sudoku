import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // 固定编译工具链：Gradle 8.12 可用、且与 miuix 0.9.2 的 klib 元数据（Kotlin 2.4.0）对齐。
    // 注意：这只是编译/测试用的 JDK；启动 Gradle 的 JAVA_HOME 同样必须指向 JDK 17+（≤24），
    //       实测 JDK 25 会让 Gradle 8.12 直接失败（详见 docs/03-开发流程.md §1）。
    jvmToolchain(21)

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)

            // miuix：Compose Multiplatform 的 HyperOS 风格组件库
            // 0.9.2 已发布 android / desktop / ios / macos / js / wasm-js 变体；
            // 本项目只启用 jvm()，Android 需 compileSdk 37（详见 docs/01-架构设计.md §7）。
            implementation(libs.miuix.ui)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain {
            // 把生成的应用图标（tools/make-icon.ps1 的产物）打进 jar：
            // jpackage 只把图标**嵌入 exe**，AWT 窗口默认仍显示 JDK 的 Java 图标，
            // 所以运行时要在 main.kt 里用这些资源设置 window.iconImages（与打包图标同源）。
            resources.srcDir("icons")
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
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
            // 图标由 tools/make-icon.ps1 生成（纸面 + 墨线九宫格），与界面同一套视觉语言；
            // macOS 需要 .icns（用 iconutil 生成），未纳入本脚本，DMG 暂时使用 jpackage 默认图标。
            windows { iconFile.set(project.file("icons/sudoku.ico")) }
            linux { iconFile.set(project.file("icons/sudoku.png")) }
        }
    }
}
