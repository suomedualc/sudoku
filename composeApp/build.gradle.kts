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
            packageName = "数独 Sudoku"
            packageVersion = "2.0.0"
        }
    }
}
