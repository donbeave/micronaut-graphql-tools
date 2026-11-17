pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "8.0.0-M16"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "graphql-tools-parent"

include("graphql-tools")
include("graphql-tools-bom")
include("graphql-tools-processor")
include("benchmarks")
include("test-suite")

configure<io.micronaut.build.MicronautBuildSettingsExtension> {
    useStandardizedProjectNames = true
    importMicronautCatalog()
    importMicronautCatalog("micronaut-graphql")
}

// The standardized names prefix every module with "micronaut-"; the benchmarks keep the short name that the contributor
// guide and CI use (:benchmarks:allocationTest), like test-suite.
gradle.settingsEvaluated {
    rootProject.children.single { it.name == "micronaut-benchmarks" }.name = "benchmarks"
}
