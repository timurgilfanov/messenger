plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(17)
}

ktlint { version.set("1.7.1") }

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
