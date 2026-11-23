// A helper module of this repository that is not published (the benchmarks and the TCK), named test-suite-*, which the
// standardized project names do not prefix. Compiled for Java 21 like the test suites that run it.
plugins {
    `java-library`
    id("io.micronaut.build.internal.graphql-tools-license")
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}
