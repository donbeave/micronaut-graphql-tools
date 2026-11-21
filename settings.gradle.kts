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
include("graphql-tools-binding")
include("graphql-tools-bom")
include("graphql-tools-scalars")
include("graphql-tools-micrometer")
include("graphql-tools-opentelemetry")
include("graphql-tools-upload")
include("graphql-tools-processor")
include("benchmarks")
include("graphql-tools-tck")
include("test-suite-java")
include("test-suite-kotlin")
include("test-suite-groovy")
include("test-suite-build")

configure<io.micronaut.build.MicronautBuildSettingsExtension> {
    useStandardizedProjectNames = true
    importMicronautCatalog()
    importMicronautCatalog("micronaut-graphql")
    importMicronautCatalog("micronaut-micrometer")
    importMicronautCatalog("micronaut-tracing")
}

gradle.settingsEvaluated {
    rootProject.children.single { it.name == "micronaut-benchmarks" }.name = "benchmarks"
    rootProject.children.single { it.name == "micronaut-graphql-tools-tck" }.name = "graphql-tools-tck"
}
