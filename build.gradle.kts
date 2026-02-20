plugins {
    kotlin("jvm") version "2.3.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.genai:google-genai:1.36.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

application {
    // Matches the top-level main function in Main.kt
    mainClass.set("MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
