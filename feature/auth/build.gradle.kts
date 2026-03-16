plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
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

    testFixtures {
        enable = true
    }

    buildFeatures {
        compose = true
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
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    // Core
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ========== Dependency Injection ==========
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // ========== Credentials ==========
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // ========== Module Dependencies ==========
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(testFixtures(project(":core:domain")))
    // Compose runtime is required because the kotlin.compose plugin applies the Compose compiler
    // to all source sets, including testFixtures which contains no Compose code.
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime)
    // lifecycle-viewmodel is not pulled in by lifecycle-runtime, so ViewModel supertype
    // is missing from the testFixtures compile classpath without this explicit dependency.
    testFixturesImplementation(libs.androidx.lifecycle.viewmodel)
    testFixturesImplementation(project(":core:domain"))
    testFixturesImplementation(testFixtures(project(":core:domain")))
    testImplementation(project(":core:test"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(testFixtures(project(":core:domain")))
    androidTestImplementation(testFixtures(project(":core:domain")))

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
