plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("io.micronaut.build.internal:micronaut-build:7.6.6")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.2.1")
}
