pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PocketLab"

include(":app")

include(":core:common")
include(":core:model")
include(":core:io")
include(":core:crypto")
include(":core:database")
include(":core:report")
include(":core:rules-model")
include(":core:testing")

include(":engine:api")
include(":engine:service")
include(":engine:orchestrator")
include(":engine:pipeline")
include(":engine:filetype")
include(":engine:archive")
include(":engine:apk")
include(":engine:dex")
include(":engine:native")
include(":engine:ioc")
include(":engine:rules")

include(":feature:onboarding")
include(":feature:home")
include(":feature:intake")
include(":feature:analysis")
include(":feature:report")
include(":feature:cases")
include(":feature:settings")
include(":feature:about")
