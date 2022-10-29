pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "6.0.1"
}


dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "graphql-tools-parent"

include("graphql-tools")
include("graphql-tools-bom")

// examples
include("doc-examples:example-java")

micronautBuild {
    importMicronautCatalog()
}
