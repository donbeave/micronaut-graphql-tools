// The user guide examples, apart from the behavioural tests of the test-suite-* modules: the guide includes their sources with `snippet::`.
plugins {
    id("io.micronaut.build.internal.graphql-tools-license")
    id("io.micronaut.library")
    groovy
}

repositories {
    mavenCentral()
}

micronaut {
    // The platform version: micronaut-build reads `micronaut` as the core BOM version, which has no platform of the same number.
    version.set(libs.versions.micronaut.platform)
    processing {
        incremental(true)
        annotations("example.*")
    }
}

dependencies {
    implementation(projects.micronautGraphqlTools)
    implementation("org.apache.groovy:groovy")
    testImplementation(mnTest.micronaut.test.spock)
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

micronaut.testRuntime("spock2")

// The Micronaut BOMs manage Groovy 4.0.28, which cannot read JDK 27 class files; 4.0.33 is the newest 4.0.x.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.groovy") {
            useVersion(libs.versions.groovy.get())
            because("Groovy 4.0.33 reads JDK 27 class files; the BOM's 4.0.28 does not")
        }
    }
}
