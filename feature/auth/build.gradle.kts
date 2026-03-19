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
        testInstrumentationRunner = "timur.gilfanov.messenger.HiltTestRunner"
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

    testFixtures {
        enable = true
    }

    buildFeatures {
        compose = true
    }
}

hilt {
    enableAggregatingTask = false
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
    implementation(libs.androidx.ui.tooling.preview)

    // Core
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Credentials
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)

    // ========== Third-party Runtime Libraries ==========
    // Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Google Identity
    implementation(libs.googleid)

    // Networking
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Kotlin Extensions
    implementation(libs.kotlinx.serialization.json)

    // Security
    implementation(libs.security.crypto)

    // ========== Test Dependencies ==========
    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Test Utilities
    testImplementation(libs.ktor.client.mock)
    kspTest(libs.hilt.compiler)

    // ========== Android Test Dependencies ==========
    androidTestImplementation(project(":core:androidTest"))
    androidTestImplementation(testFixtures(project(":core:domain")))
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    kspAndroidTest(libs.hilt.compiler)

    // ========== Debug Dependencies ==========
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ========== Module Dependencies ==========
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(testFixtures(project(":core:domain")))
    testImplementation(project(":core:test"))
    testImplementation(testFixtures(project(":core:domain")))
    testFixturesImplementation(project(":core:domain"))
    testFixturesImplementation(testFixtures(project(":core:domain")))
    // Compose runtime is required because the kotlin.compose plugin applies the Compose compiler
    // to all source sets, including testFixtures which contains no Compose code.
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime)
    // lifecycle-viewmodel is not pulled in by lifecycle-runtime, so ViewModel supertype
    // is missing from the testFixtures compile classpath without this explicit dependency.
    testFixturesImplementation(libs.androidx.lifecycle.viewmodel)
    testFixturesImplementation(libs.androidx.lifecycle.viewmodel.savedstate)
    testFixturesImplementation(libs.kotlinx.coroutines.core)

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
