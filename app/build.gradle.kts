plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application

    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // Project dependencies
    implementation(project(":utils"))

    // Kotlinx ecosystem
    implementation(libs.bundles.kotlinxEcosystem)

    // Ktor client for HTTP communication
    implementation(libs.bundles.ktorClient)

    // Koog AI Agent framework with MCP support
    implementation(libs.bundles.koog)

    // Telegram bot library
    implementation(libs.telegramBot)

    // Environment variables management
    implementation(libs.dotenv)

    // Dependency Injection
    implementation(libs.koinCore)
    implementation(libs.koinLogger)

    // Logging
    implementation(libs.slf4jSimple)
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `io.github.devapro.ai.AppKt`.)
    mainClass = "io.github.devapro.ai.AppKt"
}

// Configure the JAR task to create a fat JAR with all dependencies
tasks.jar {
    manifest {
        attributes["Main-Class"] = "io.github.devapro.ai.AppKt"
    }

    // Create a fat JAR with all dependencies
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
