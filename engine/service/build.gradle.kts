plugins {
    id("pocketlab.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pineandpackets.pocketlab.engine.service"

    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":engine:api"))
    implementation(project(":engine:pipeline"))
    implementation(project(":engine:filetype"))
    implementation(project(":engine:archive"))
    implementation(project(":engine:apk"))
    implementation(project(":engine:dex"))
    implementation(project(":engine:ioc"))
    implementation(project(":engine:rules"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
