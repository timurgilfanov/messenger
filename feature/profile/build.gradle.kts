plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

android {
    namespace = "timur.gilfanov.messenger.profile"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "timur.gilfanov.messenger.HiltTestRunner"
    }

    buildTypes {
        debug {
            enableAndroidTestCoverage = project.hasProperty("coverage")
        }
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)

    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
    kspTest(libs.hilt.compiler)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    testImplementation(project(":core:test"))
    testImplementation(testFixtures(project(":core:domain")))
    testFixturesImplementation(project(":core:domain"))
    testFixturesImplementation(testFixtures(project(":core:domain")))
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime)
    testFixturesImplementation(libs.androidx.lifecycle.viewmodel)
    testFixturesImplementation(libs.androidx.lifecycle.viewmodel.savedstate)
    testFixturesImplementation(libs.kotlinx.coroutines.core)

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

tasks.register("generateCategorySpecificReports") {
    group = "verification"
    description = "Generate category-specific coverage reports"

    doLast {
        val category = project.findProperty("testCategory") as String?
        if (category != null) {
            val categoryName = category.substringAfterLast(".")
            val buildType = project.findProperty("buildType") as String?
                ?: throw GradleException("buildType parameter is required")
            val buildVariant = buildType.replaceFirstChar { it.uppercase() }
            val sourceReportFile = file(
                "build/reports/kover/report${buildVariant.replaceFirstChar { it.uppercase() }}.xml",
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
                        "Generate coverage report first.",
                )
            }
        }
    }
}
