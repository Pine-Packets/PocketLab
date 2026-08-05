plugins {
    id("pocketlab.android.library")
    id("pocketlab.android.compose")
}

android {
    namespace = "com.pineandpackets.pocketlab.feature.about"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
}
