plugins {
    kotlin("jvm") version "2.3.0"
    application
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.google.genai:google-genai:1.36.0")
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.3.0")
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
