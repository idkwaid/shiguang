buildscript {
    dependencies {
        // AGP 9.5 内置 Kotlin 为 2.2.10，读不了 Kotlin 2.4 元数据（Compose 1.13 / Coil 3.6 等）。
        // 显式把 Kotlin Gradle Plugin 提到 2.4.0，让内置 Kotlin 用 2.4 编译器。
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

// 拾光（Lumo）根构建脚本
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
