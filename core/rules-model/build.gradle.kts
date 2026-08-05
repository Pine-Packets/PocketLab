plugins {
    id("pocketlab.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pineandpackets.pocketlab.core.rules.model"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}
