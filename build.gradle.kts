buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 走 buildscript 类路径（而非 plugins{} DSL）：KGP 2.4 的
        // AndroidGradlePluginVersion 探测经由 buildscript 类加载器做反射，
        // plugins{} 的隔离类加载器会让它报 "Can't infer current AndroidGradlePluginVersion"。
        // 升级 AGP 时改这里与 composeApp/plugins 里的 id("com.android.application") 保持同源。
        classpath("com.android.tools.build:gradle:8.10.1")
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}
