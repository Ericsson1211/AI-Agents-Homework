plugins {
    id("java")
    id("application")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
}

application {
    mainClass.set("MainKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

group = "sk.erikpaller"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Langchain4j core
    implementation ("dev.langchain4j:langchain4j:1.0.0-alpha1")

    // Langchain4j with Ollama (local LLM)
    implementation ("dev.langchain4j:langchain4j-ollama:1.0.0-alpha1")

    // Langchain4j with OpenAI (optional, for OpenAI API)
    implementation ("dev.langchain4j:langchain4j-open-ai:1.0.0-alpha1")

    // HTTP client for GitHub API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // JSON parsing
    implementation ("com.google.code.gson:gson:2.10.1")
    // Logging
    implementation ("org.slf4j:slf4j-simple:2.0.9")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}