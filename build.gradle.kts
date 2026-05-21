plugins {
    kotlin("jvm") version "2.3.20"
    application
}

group = "de.ljunker"
version = "0.1.0"

application {
    mainClass.set("de.ljunker.kasm.MainKt")
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
