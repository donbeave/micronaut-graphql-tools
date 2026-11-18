// What a consumer's settings file needs on top of a Micronaut library project: the plugin version, and the repository that
// holds the library under test. The test passes the values as Gradle properties.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("io.micronaut.library") version providers.gradleProperty("micronautGradlePluginVersion").get()
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

rootProject.name = "multi-module-gradle"

// The schema lives in its own module, the resolvers in a feature module and the application depends on it. Only the feature
// module applies the processor, so a schema edit recompiles the feature module and nothing else.
include("schema", "feature", "app")
