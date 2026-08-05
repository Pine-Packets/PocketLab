plugins {
    id("pocketlab.android.library")
    id("pocketlab.android.compose")
}

android {
    namespace = "com.pineandpackets.pocketlab.feature.onboarding"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
}
