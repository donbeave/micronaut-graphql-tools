plugins {
    id("io.micronaut.build.internal.docs")
    id("io.micronaut.build.internal.dependency-updates")
    id("io.micronaut.build.internal.quality-reporting")
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }
}
