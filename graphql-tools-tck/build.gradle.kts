plugins {
    id("java-library")
}

// The execution-behaviour TCK (#53): the scenarios (schema, operations, expected responses) and the runner that the
// test-suite-java, test-suite-kotlin and test-suite-groovy suites run against their own fixtures. Nothing here is published.
//   src/main/resources/tck   the scenarios, one directory each; see CONTRIBUTING.md ("Adding a TCK scenario")
//   src/main/java            the runner: it starts the suite's application per scenario and compares raw HTTP responses
//   src/test                 checks of the scenario files themselves (well-formed, complete, listed)

repositories {
    mavenCentral()
}

// Compiled for Java 21 like the test-suite-* modules that run it.
java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

dependencies {
    // The runner starts the application of the suite that runs it, so the suite provides the server and the JSON stack.
    api(mn.micronaut.runtime)
    api(mn.micronaut.json.core)

    testImplementation(platform(libs.micronaut.platform))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(mn.micronaut.jackson.databind)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
