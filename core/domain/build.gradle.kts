plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(libs.versions.javaVersion.get().toInt())
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

ktlint { version.set(libs.versions.ktlintTool.get()) }

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.paging.common)

    testImplementation(project(":core:test"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.paging.testing)

    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.kotlinx.collections.immutable)
    testFixturesImplementation(libs.androidx.paging.common)

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

// Category-specific coverage report tasks
tasks.register("generateCategorySpecificReports") {
    group = "verification"
    description = "Generate category-specific coverage reports"

    doLast {
        // After tests are run with coverage, copy the main report to category-specific files
        val category = project.findProperty("testCategory") as String?
        if (category != null) {
            val categoryName = category.substringAfterLast(".")

            val sourceReportFile = file("build/reports/kover/report.xml")

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
