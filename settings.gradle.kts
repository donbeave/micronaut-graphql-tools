pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "6.5.3"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "graphql-tools-parent"

include("graphql-tools")
include("graphql-tools-bom")

// examples
include("doc-examples:example-java")

configure<io.micronaut.build.MicronautBuildSettingsExtension> {
    useStandardizedProjectNames = true
    importMicronautCatalog()
    importMicronautCatalog("micronaut-graphql")
}
