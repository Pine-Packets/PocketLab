plugins {
    id("pocketlab.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pineandpackets.pocketlab.core.testing"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.junit)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.kotest.runner.junit5)
    implementation(libs.kotest.assertions.core)
    implementation(libs.kotest.property)
    implementation(libs.mockk)
    implementation(libs.turbine)
}
