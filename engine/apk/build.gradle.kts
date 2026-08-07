plugins {
    id("pocketlab.android.library")
}

android {
    namespace = "com.pineandpackets.pocketlab.engine.apk"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":engine:api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)
    implementation(libs.apksig)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}
