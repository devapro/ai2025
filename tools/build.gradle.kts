plugins {
    // Apply the shared build logic from a convention plugin.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin
    alias(libs.plugins.kotlinPluginSerialization)
    // Apply Application plugin to make it executable
    application
}

dependencies {
    // Project dependencies
    implementation(project(":utils-embeds"))

    // Kotlinx ecosystem (serialization, coroutines)
    implementation(libs.bundles.kotlinxEcosystem)

    // Ktor client for HTTP communication (OpenAI API)
    implementation(libs.bundles.ktorClient)

    // Token counting for GPT models
    implementation(libs.jtokkit)

    // Logging
    implementation(libs.slf4jSimple)

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.github.devapro.ai.tools.MainKt")
}

tasks.named<JavaExec>("run") {
    // Set working directory to project root for file operations
    workingDir = rootProject.projectDir
    // Support reading from stdin
    standardInput = System.`in`
}