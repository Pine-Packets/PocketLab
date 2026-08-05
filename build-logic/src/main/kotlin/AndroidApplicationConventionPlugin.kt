import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.JavaVersion
import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            
            val android = extensions.getByType(ApplicationExtension::class.java)
            
            android.apply {
                compileSdk = 36
                
                defaultConfig {
                    applicationId = "com.pineandpackets.pocketlab"
                    minSdk = 29
                    targetSdk = 36
                    versionCode = 1
                    versionName = "1.0.0"
                    
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    
                    vectorDrawables {
                        useSupportLibrary = true
                    }
                }
                
                buildTypes {
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                    debug {
                        isMinifyEnabled = false
                        applicationIdSuffix = ".debug"
                    }
                }
                
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                
                buildFeatures {
                    compose = true
                    buildConfig = true
                }
                
                composeOptions {
                    kotlinCompilerExtensionVersion = "1.5.15"
                }
                
                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }
            }
            
            tasks.withType(KotlinCompile::class.java).configureEach {
                kotlinOptions {
                    jvmTarget = "17"
                    freeCompilerArgs += listOf(
                        "-opt-in=kotlin.RequiresOptIn",
                        "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                        "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                        "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
                    )
                }
            }
        }
    }
}
