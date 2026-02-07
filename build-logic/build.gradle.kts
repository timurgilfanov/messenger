plugins {
    kotlin("jvm") version "2.2.0"
}

dependencies {
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:1.23.8")
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.0")
    testImplementation("io.gitlab.arturbosch.detekt:detekt-test:1.23.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
