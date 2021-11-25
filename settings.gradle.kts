dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("com.android.application") version "7.0.3"
        id("com.android.library") version "7.0.3"
        id("org.jetbrains.kotlin.android") version "1.6.0"
    }
}

include(":app", ":library")
