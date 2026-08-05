import org.gradle.api.Plugin
import org.gradle.api.Project
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            
            val appExtension = extensions.findByType(ApplicationExtension::class.java)
            val libExtension = extensions.findByType(LibraryExtension::class.java)
            
            appExtension?.apply {
                buildFeatures.compose = true
            }
            
            libExtension?.apply {
                buildFeatures.compose = true
            }
        }
    }
}
