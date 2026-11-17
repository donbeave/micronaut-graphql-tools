pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "7.6.6"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "graphql-tools-parent"

include("graphql-tools")
include("graphql-tools-bom")
include("graphql-tools-processor")
include("benchmarks")
include("test-suite-java")
include("test-suite-kotlin")
include("test-suite-groovy")

configure<io.micronaut.build.MicronautBuildSettingsExtension> {
    useStandardizedProjectNames = true
    importMicronautCatalog()
    importMicronautCatalog("micronaut-graphql")
}

gradle.settingsEvaluated {
    rootProject.children.single { it.name == "micronaut-benchmarks" }.name = "benchmarks"
}
