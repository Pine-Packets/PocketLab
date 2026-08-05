plugins {
    id("pocketlab.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pineandpackets.pocketlab.core.crypto"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}
