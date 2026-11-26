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

dependencies {
    testImplementation(platform(libs.micronaut.platform))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
