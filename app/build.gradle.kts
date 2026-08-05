plugins {
    id("pocketlab.android.application")
    id("pocketlab.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pineandpackets.pocketlab"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:io"))
    implementation(project(":core:crypto"))
    implementation(project(":core:database"))
    implementation(project(":core:report"))
    implementation(project(":core:rules-model"))

    implementation(project(":engine:api"))
    implementation(project(":engine:orchestrator"))
    implementation(project(":engine:filetype"))

    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:intake"))
    implementation(project(":feature:analysis"))
    implementation(project(":feature:report"))
    implementation(project(":feature:cases"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:about"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.bundles.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.mockk.android)
}
