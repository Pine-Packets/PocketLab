plugins {
    id("pocketlab.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pineandpackets.pocketlab.core.report"

    sourceSets["main"].resources.srcDir("../../test-corpus")
    sourceSets["test"].resources.srcDir("../../test-corpus")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
}
