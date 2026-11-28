// What a consumer's settings file needs on top of a Micronaut library project: the plugin versions, and the repository that
// holds the library under test. The test passes the values as Gradle properties.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("io.micronaut.library") version providers.gradleProperty("micronautGradlePluginVersion").get()
        id("org.jetbrains.kotlin.jvm") version providers.gradleProperty("kotlinVersion").get()
        id("com.google.devtools.ksp") version providers.gradleProperty("kspVersion").get()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven(url = providers.gradleProperty("graphqlToolsRepository").get())
    }
}

buildCache {
    local {
        providers.gradleProperty("sampleBuildCache").orNull?.let { directory = file(it) }
    }
}

rootProject.name = "kotlin-ksp-client"
