plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(libs.versions.javaVersion.get().toInt())
}

ktlint { version.set(libs.versions.ktlintTool.get()) }

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(libs.junit)
    implementation(libs.kotlinx.coroutines.test)

    detektPlugins(libs.detekt.formatting)
    detektPlugins(project(":build-logic"))
}
