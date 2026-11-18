plugins {
    id("io.micronaut.library")
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

micronaut {
    version.set(providers.gradleProperty("micronautPlatformVersion"))
}

dependencies {
    implementation(project(":feature"))
    runtimeOnly(project(":schema"))
}
