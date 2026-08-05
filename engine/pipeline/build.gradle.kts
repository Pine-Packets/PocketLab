plugins {
    id("pocketlab.android.library")
}

android {
    namespace = "com.pineandpackets.pocketlab.engine.pipeline"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":engine:api"))
    implementation(project(":engine:filetype"))
    implementation(project(":engine:archive"))
    implementation(project(":engine:apk"))
    implementation(project(":engine:dex"))
    implementation(project(":engine:ioc"))
    implementation(project(":engine:rules"))
    
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}
