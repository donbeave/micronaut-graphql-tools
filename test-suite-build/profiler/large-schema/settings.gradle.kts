pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version providers.gradleProperty("kotlinVersion").get()
        id("com.google.devtools.ksp") version providers.gradleProperty("kspVersion").get()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "large-schema"

include("probe", "javac", "ksp", "groovy")
