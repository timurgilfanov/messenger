plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    alias(libs.plugins.hilt)
    id("kotlin-kapt")
}

android {
    namespace = "timur.gilfanov.messenger"
    compileSdk = 35

    defaultConfig {
        applicationId = "timur.gilfanov.messenger"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    buildFeatures {
        compose = true
    }
}

ktlint {
    version.set("1.4.1") // restricted by compose ruleset for ktlint, waiting for 0.4.23 release
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

hilt {
    enableAggregatingTask = false
}

kover {
    reports {
        filters {
            excludes {
                annotatedBy(
                    "androidx.compose.ui.tooling.preview.Preview",
                    "dagger.internal.DaggerGenerated",
                    "javax.annotation.processing.Generated",
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

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    kapt(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.konsist)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)
    testImplementation(project(":test-annotations"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(project(":test-annotations"))
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    ktlintRuleset(libs.ktlint.compose)
    detektPlugins(libs.detekt.compose)
    detektPlugins(libs.detekt.formatting)
    implementation(libs.orbit.core)
    implementation(libs.orbit.viewmodel)
    implementation(libs.orbit.compose)
    testImplementation(libs.orbit.test)
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
            copy {
                from("build/reports/kover/reportDebug.xml")
                into("build/reports/kover/")
                rename { "${categoryName.lowercase()}-report.xml" }
            }
            println("Generated category-specific report: ${categoryName.lowercase()}-report.xml")
        }
    }
}

tasks.register("testArchitectureWithCoverage") {
    group = "verification"
    description = "Run Architecture tests with coverage"
    doLast {
        val process = ProcessBuilder(
            "./gradlew",
            "testDebugUnitTest",
            "-PtestCategory=timur.gilfanov.annotations.Architecture",
            "-Pcoverage",
        )
            .directory(project.rootDir)
            .inheritIO()
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("Architecture tests failed with exit code $exitCode")
        }
    }
}

tasks.register("testUnitWithCoverage") {
    group = "verification"
    description = "Run Unit tests with coverage"
    doLast {
        val process = ProcessBuilder(
            "./gradlew",
            "testDebugUnitTest",
            "-PtestCategory=timur.gilfanov.annotations.Unit",
            "-Pcoverage",
        )
            .directory(project.rootDir)
            .inheritIO()
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("Unit tests failed with exit code $exitCode")
        }
    }
}

tasks.register("testComponentWithCoverage") {
    group = "verification"
    description = "Run Component tests with coverage"
    doLast {
        val process = ProcessBuilder(
            "./gradlew",
            "testDebugUnitTest",
            "-PtestCategory=timur.gilfanov.annotations.Component",
            "-Pcoverage",
        )
            .directory(project.rootDir)
            .inheritIO()
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("Component tests failed with exit code $exitCode")
        }
    }
}

tasks.register("preCommit") {
    group = "verification"
    description = "Run all pre-commit checks locally"

    dependsOn("ktlintFormat", "lintDebug", "detekt")

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
                "testDebugUnitTest",
                "-PtestCategory=timur.gilfanov.annotations.$category",
                "-Pcoverage",
            )
                .directory(project.rootDir)
                .inheritIO()
                .start()

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw GradleException(
                    "$category tests failed with exit code $exitCode",
                )
            }
        }

        println("")
        println("✅ All pre-commit checks passed! Coverage reports generated.")
        println("📊 Coverage reports available in app/build/reports/kover/")
    }
}
