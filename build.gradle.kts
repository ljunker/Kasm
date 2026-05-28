plugins {
    kotlin("jvm") version "2.3.20"
    application
}

group = "de.ljunker"
version = "0.8.0"

application {
    mainClass.set("de.ljunker.kasm.MainKt")
    applicationName = "kasm"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
