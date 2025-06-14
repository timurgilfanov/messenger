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

    sourceSets {
        getByName("test") {
            java.srcDir("src/testShared/java")
        }
        getByName("androidTest") {
            java.srcDir("src/testShared/java")
        }
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
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.kotlin.test)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
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

// Custom test tasks for categories (based on Testing Strategy.md)
tasks.register("testUnit") {
    group = "verification"
    description = "Run only Unit tests"
    dependsOn("testDebugUnitTest")
    doFirst {
        tasks.withType<Test> {
            useJUnit {
                includeCategories("timur.gilfanov.messenger.Unit")
            }
        }
    }
}

tasks.register("testComponent") {
    group = "verification"
    description = "Run only Component tests"
    dependsOn("testDebugUnitTest")
    doFirst {
        tasks.withType<Test> {
            useJUnit {
                includeCategories("timur.gilfanov.messenger.Component")
            }
        }
    }
}

tasks.register("testArchitecture") {
    group = "verification"
    description = "Run only Architecture tests"
    dependsOn("testDebugUnitTest")
    doFirst {
        tasks.withType<Test> {
            useJUnit {
                includeCategories("timur.gilfanov.messenger.Architecture")
            }
        }
    }
}

tasks.register("testFeature") {
    group = "verification"
    description = "Run only Feature tests"
    dependsOn("testDebugUnitTest")
    doFirst {
        tasks.withType<Test> {
            useJUnit {
                includeCategories("timur.gilfanov.messenger.Feature")
            }
        }
    }
}

tasks.register("testApplication") {
    group = "verification"
    description = "Run only Application tests (both unit and instrumentation)"
    dependsOn("testDebugUnitTest", "connectedDebugAndroidTest")
    doFirst {
        tasks.withType<Test> {
            useJUnit {
                includeCategories("timur.gilfanov.messenger.Application")
            }
        }
    }
}

tasks.register("testReleaseCandidate") {
    group = "verification"
    description = "Run only Release Candidate tests"
    dependsOn("connectedReleaseAndroidTest")
    doFirst {
        tasks.withType<Test> {
            useJUnit {
                includeCategories("timur.gilfanov.messenger.ReleaseCandidate")
            }
        }
    }
}
