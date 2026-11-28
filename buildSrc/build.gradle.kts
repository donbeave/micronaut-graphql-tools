plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("io.micronaut.build.internal:micronaut-build:7.6.6")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.12.0")
}

dependencies {
    testImplementation(platform(libs.micronaut.platform))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
