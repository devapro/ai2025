plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)
    // Apply Application plugin to make it executable
    application
}

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog (`gradle/libs.versions.toml`).
    implementation(libs.bundles.kotlinxEcosystem)

    // Ktor client for OpenAI API calls
    implementation(libs.bundles.ktorClient)

    // Database dependencies
    implementation(libs.bundles.exposed)
    implementation(libs.sqliteJdbc)

    // Logging
    implementation(libs.slf4jSimple)

    // Dotenv for environment variables
    implementation(libs.dotenv)

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.github.devapro.ai.utils.UtilAppKt")
}

tasks.named<JavaExec>("run") {
    // Set working directory to project root so dotenv can find .env file
    workingDir = rootProject.projectDir
}