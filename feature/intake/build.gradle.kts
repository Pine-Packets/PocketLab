plugins {
    id("pocketlab.android.library")
    id("pocketlab.android.compose")
}

android {
    namespace = "com.pineandpackets.pocketlab.feature.intake"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:io"))
    implementation(project(":core:database"))
    implementation(project(":engine:api"))
    implementation(project(":engine:archive"))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
