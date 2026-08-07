plugins {
    id("pocketlab.android.library")
}

android {
    namespace = "com.pineandpackets.pocketlab.engine.archive"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":engine:api"))
    implementation(project(":engine:apk"))
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
}
