plugins {
    id("io.micronaut.application")
    id("com.gradleup.shadow")
    groovy
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.micronautGraphqlTools)
    // The Groovy runtime of the application classes. Nothing else puts it on the runtime classpath: the tests got it from
    // Spock, so the application itself (run, shadowJar) was missing it.
    implementation("org.apache.groovy:groovy")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation(projects.graphqlToolsTck) // the execution-behaviour TCK (#53)
}

application {
    mainClass.set("example.Application")
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

// The Groovy suite runs on the JVM only (#55); there is no native test.
micronaut {
    // The platform version: micronaut-build reads `micronaut` as the core BOM version, which has no platform of the same number.
    version.set(libs.versions.micronaut.platform)
    runtime("netty")
    testRuntime("spock2")
    processing {
        incremental(true)
        annotations("example.*")
    }
}

// The Micronaut BOMs manage Groovy 4.0.28, which cannot read JDK 27 class files; 4.0.33 is the newest 4.0.x (D-34).
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.groovy") {
            useVersion(libs.versions.groovy.get())
            because("Groovy 4.0.33 reads JDK 27 class files; the BOM's 4.0.28 does not")
        }
    }
}
