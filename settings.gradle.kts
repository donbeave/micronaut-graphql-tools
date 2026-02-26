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
include("test-suite")

configure<io.micronaut.build.MicronautBuildSettingsExtension> {
    useStandardizedProjectNames = true
    importMicronautCatalog()
    importMicronautCatalog("micronaut-graphql")
}
