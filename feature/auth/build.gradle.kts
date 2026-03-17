plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "timur.gilfanov.messenger.auth"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(libs.versions.javaVersion.get())
        }
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }

    buildFeatures {
        compose = true
    }

    testFixtures {
        enable = true
    }
}

ktlint { version.set(libs.versions.ktlintTool.get()) }

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    // ========== Core AndroidX ==========
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)

    // ========== Module Dependencies ==========
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    testImplementation(project(":core:test"))
    testImplementation(testFixtures(project(":core:domain")))
    androidTestImplementation(testFixtures(project(":core:domain")))

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    kspAndroidTest(libs.hilt.compiler)
    kspTest(libs.hilt.compiler)

    // Networking
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    // Security
    implementation(libs.security.crypto)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.ktor.client.mock)

    // testFixtures deps
    testFixturesImplementation(project(":core:domain"))
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime)

    // ========== Dev Tool Dependencies ==========
    ktlintRuleset(libs.ktlint.compose)
    detektPlugins(libs.detekt.compose)
    detektPlugins(libs.detekt.formatting)
    detektPlugins(project(":build-logic"))
}

tasks.withType<Test> {
    useJUnit {
        if (project.hasProperty("testCategory")) {
            includeCategories(project.property("testCategory") as String)
        }
    }
}
