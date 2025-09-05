plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("jacoco")
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

android {
    namespace = "timur.gilfanov.messenger"
    compileSdk = 36

    defaultConfig {
        applicationId = "timur.gilfanov.messenger"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "timur.gilfanov.messenger.HiltTestRunner"
        testInstrumentationRunnerArguments += mapOf("clearPackageData" to "true")

        // Add annotation filtering for instrumentation tests
        if (project.hasProperty("annotation")) {
            testInstrumentationRunnerArguments += mapOf(
                "annotation" to (project.property("annotation") as String),
            )
        }

        // Build config fields for API configuration
        buildConfigField("String", "API_BASE_URL", "\"https://api.messenger.example.com/v1\"")
        buildConfigField("boolean", "USE_REAL_REMOTE_DATA_SOURCES", "false")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildTypes {
        debug {
            enableAndroidTestCoverage = project.hasProperty("coverage")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    flavorDimensions += "environment"
    productFlavors {
        create("mock") {
            dimension = "environment"
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://mock.api.messenger.example.com/v1\"",
            )
            buildConfigField("boolean", "USE_REAL_REMOTE_DATA_SOURCES", "false")
        }
        create("dev") {
            dimension = "environment"
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://dev.api.messenger.example.com/v1\"",
            )
            buildConfigField("boolean", "USE_REAL_REMOTE_DATA_SOURCES", "true")
        }
        create("staging") {
            dimension = "environment"
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://staging.api.messenger.example.com/v1\"",
            )
            buildConfigField("boolean", "USE_REAL_REMOTE_DATA_SOURCES", "true")
        }
        create("production") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"https://api.messenger.example.com/v1\"")
            buildConfigField("boolean", "USE_REAL_REMOTE_DATA_SOURCES", "true")
        }
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_metrics")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

ktlint {
    version.set("1.7.1") // change default version of ktlint in ktlint gradle plugin
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

hilt {
    enableAggregatingTask = false
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kover {
    reports {
        filters {
            excludes {
                annotatedBy(
                    "androidx.compose.ui.tooling.preview.Preview",
                    "dagger.internal.DaggerGenerated",
                    "javax.annotation.processing.Generated",
                    "timur.gilfanov.messenger.annotation.KoverIgnore",
                )
                classes(
                    "*.Hilt_*",
                    "*_HiltModules*",
                    "*_Factory*",
                    "*_MembersInjector*",
                    "dagger.hilt.internal.*",
                    "*ComposableSingletons*",
                    "hilt_aggregated_deps.*",
                    "Dagger*",
                    "*_ComponentTreeDeps*",
                    "*_HiltComponents*",
                    "timur.gilfanov.messenger.di.*",
                )
            }
        }
    }
}

tasks.register<JacocoReport>("jacocoExternalCoverageReport") {
    group = "verification"
    description = "Generate JaCoCo coverage report from external .ec files passed via parameters"

    // Get build variant parameters (required)
    val buildFlavor = project.findProperty("buildFlavor") as String?
        ?: throw GradleException("buildFlavor parameter is required")
    val buildType = project.findProperty("buildType") as String?
        ?: throw GradleException("buildType parameter is required")
    val buildVariant = "${buildFlavor}${buildType.replaceFirstChar { it.uppercase() }}"

    reports {
        xml.required.set(true)
        html.required.set(true)
        xml.outputLocation.set(
            layout.buildDirectory.file("reports/jacoco/firebaseTestLab/jacocoTestReport.xml"),
        )
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/firebaseTestLab/html"))
    }

    sourceDirectories.setFrom(files("$projectDir/src/main/java"))
    val excludePatterns = listOf(
        // Hilt generated classes
        "**/*Hilt_*",
        "**/*_HiltModules*",
        "**/*_Factory*",
        "**/*_MembersInjector*",
        "**/dagger/hilt/internal/**",
        "**/hilt_aggregated_deps/**",
        "**/Dagger*",
        "**/*_ComponentTreeDeps*",
        "**/*_HiltComponents*",
        "**/timur/gilfanov/messenger/di/**",
        // Compose generated classes
        "**/*ComposableSingletons*",
        // Preview functions
        "**/*Preview*",
        "**/*PreviewKt*",
    )
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/$buildVariant")) {
            exclude(excludePatterns)
        } + fileTree(layout.buildDirectory.dir("intermediates/javac/$buildVariant/classes")) {
            exclude(excludePatterns)
        },
    )

    // External coverage files passed via parameter
    val externalCoverageFiles = project.findProperty("externalCoverageFiles")?.toString()
    val coverageFiles = externalCoverageFiles?.split(",")?.map {
        project.rootProject.file(it.trim())
    }

    if (coverageFiles != null) {
        executionData.setFrom(coverageFiles)
        println("Using external coverage files: $coverageFiles")
    } else {
        throw GradleException(
            "externalCoverageFiles parameter is required. Provide comma-separated .ec file paths.",
        )
    }
}

dependencies {
    // ========== Core AndroidX ==========
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Navigation
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Data Storage
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // ========== Third-party Runtime Libraries ==========
    // Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Networking
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // Architecture
    implementation(libs.orbit.core)
    implementation(libs.orbit.viewmodel)
    implementation(libs.orbit.compose)

    // Image Loading
    implementation(libs.coil.compose)

    // Kotlin Extensions
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    // ========== Test Dependencies ==========
    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.orbit.test)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)

    // Android Testing
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)

    // Compose Testing
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)

    // Architecture Testing
    testImplementation(libs.konsist)

    // Test Utilities
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.paging.testing)

    // ========== Android Test Dependencies ==========
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    // ========== Debug Dependencies ==========
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ========== Dev Tool Dependencies ==========
    ktlintRuleset(libs.ktlint.compose)
    detektPlugins(libs.detekt.compose)
    detektPlugins(libs.detekt.formatting)
}

tasks.withType<Test> {
    useJUnit {
        if (project.hasProperty("testCategory")) {
            includeCategories(project.property("testCategory") as String)
        }
        if (project.hasProperty("excludeCategory")) {
            excludeCategories(project.property("excludeCategory") as String)
        }
    }
}

// Category-specific coverage report tasks
tasks.register("generateCategorySpecificReports") {
    group = "verification"
    description = "Generate category-specific coverage reports"

    doLast {
        // After tests are run with coverage, copy the main report to category-specific files
        val category = project.findProperty("testCategory") as String?
        if (category != null) {
            val categoryName = category.substringAfterLast(".")

            // Use flavor-specific report file (dynamic based on build variant)
            val buildFlavor = project.findProperty("buildFlavor") as String?
                ?: throw GradleException("buildFlavor parameter is required")
            val buildType = project.findProperty("buildType") as String?
                ?: throw GradleException("buildType parameter is required")
            val buildVariant = "${buildFlavor}${buildType.replaceFirstChar { it.uppercase() }}"
            val sourceReportFile = file(
                "build/reports/kover/report${
                    buildVariant.replaceFirstChar { it.uppercase() }
                }.xml",
            )

            if (sourceReportFile.exists()) {
                copy {
                    from(sourceReportFile)
                    into("build/reports/kover/")
                    rename { "${categoryName.lowercase()}-report.xml" }
                }
                println(
                    "Generated category-specific report: ${categoryName.lowercase()}-report.xml",
                )
            } else {
                throw GradleException(
                    "Coverage report file not found: ${sourceReportFile.absolutePath}. " +
                        "Run './gradlew koverXmlReportMockDebug' first.",
                )
            }
        }
    }
}

tasks.register("checkScreenshotSize") {
    group = "verification"
    description = "Check that screenshots directory doesn't exceed size limit"

    doLast {
        val screenshotsDir = file("src/test/screenshots")
        if (screenshotsDir.exists()) {
            val maxSizeMB = 50
            val currentSizeMB = screenshotsDir.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum() / (1024 * 1024)

            if (currentSizeMB > maxSizeMB) {
                val errorMessage = buildString {
                    appendLine("❌ Screenshots directory size limit exceeded!")
                    appendLine("   Current size: ${currentSizeMB}MB")
                    appendLine("   Maximum allowed: ${maxSizeMB}MB")
                    appendLine("")
                    appendLine("   Please consider:")
                    appendLine("   • Removing old/unused screenshots")
                    appendLine("   • Optimizing image compression")
                    appendLine("   • Moving to Git LFS if needed")
                }
                throw GradleException(errorMessage)
            } else {
                println("📸 Screenshots directory size: ${currentSizeMB}MB (limit: ${maxSizeMB}MB)")
            }
        } else {
            println("📸 Screenshots directory not found, skipping size check")
        }
    }
}

tasks.register("preCommit") {
    group = "verification"
    description = "Run all pre-commit checks locally"

    dependsOn("ktlintFormat", "lintMockDebug", "detekt", "checkScreenshotSize")

    doLast {
        println("✅ Pre-commit formatting, lint, and static analysis complete!")
        println("")
        println("Running test categories with coverage...")

        val categories = listOf(
            "Architecture" to "🔧",
            "Unit" to "🧪",
            "Component" to "🔩",
        )

        categories.forEach { (category, emoji) ->
            println("$emoji Running $category tests with coverage...")

            val process = ProcessBuilder(
                "./gradlew",
                "testMockDebugUnitTest",
                "-PtestCategory=timur.gilfanov.messenger.annotations.$category",
                "-Pcoverage",
            )
                .directory(project.rootDir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                val errorMessage = buildString {
                    appendLine("$category tests failed with exit code $exitCode")
                    appendLine()
                    appendLine("Error output:")
                    appendLine(output.takeLast(2000))
                }
                throw GradleException(errorMessage)
            }
        }

        println("")
        println("✅ All pre-commit checks passed! Coverage reports generated.")
        println("📊 Coverage reports available in app/build/reports/kover/")
    }
}
