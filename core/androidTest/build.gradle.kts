plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "timur.gilfanov.messenger.androidtest"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    java { toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.javaVersion.get()) } }
    kotlin { compilerOptions { freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime") } }
}

ktlint { version.set(libs.versions.ktlintTool.get()) }
detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(libs.androidx.test.runner)
    implementation(libs.hilt.android.testing)
    detektPlugins(libs.detekt.formatting)
    detektPlugins(project(":build-logic"))
}
