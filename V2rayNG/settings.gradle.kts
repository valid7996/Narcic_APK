pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("com.android.settings") version "9.3.1"
}

rootProject.name = "v2rayNG"
include(":app")
include(":tunnel")
project(":tunnel").projectDir = File(rootDir, "../amneziawg-android/tunnel")

configure<com.android.build.api.dsl.SettingsExtension> {
    buildToolsVersion = "36.0.0"
    compileSdk = 37
    minSdk = 24
    ndkVersion = "26.1.10909125"
}
