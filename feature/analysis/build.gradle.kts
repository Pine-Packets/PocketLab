plugins {
    id("pocketlab.android.library")
    id("pocketlab.android.compose")
}

android {
    namespace = "com.pineandpackets.pocketlab.feature.analysis"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:crypto"))
    implementation(project(":core:database"))
    implementation(project(":core:io"))
    implementation(project(":core:model"))
    implementation(project(":engine:api"))
    implementation(project(":engine:orchestrator"))
    implementation(project(":engine:pipeline"))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
