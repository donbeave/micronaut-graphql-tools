plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

// A plain JSR-269 annotation processor: it checks the Java bindings of a compilation against the GraphQL schema files
// and fails javac on drift. graphql-java parses the schema on the processor path; nothing of the runtime is needed.
dependencies {
    implementation(mnGraphql.graphql.java)

    testImplementation(projects.micronautGraphqlTools)
    testImplementation(mnTest.micronaut.test.spock)
    // parity tests compile fixtures with Micronaut's processors too and start them, see Compilation#withMicronaut()
    testImplementation(mn.micronaut.inject.java)
    testRuntimeOnly(libs.logback.classic)
}

micronautBuild {
    binaryCompatibility {
        // No version of this project has been released yet, so `findBaseline` has nothing to compare against.
        // Switch to `enabledAfter("<first release>")` once the first version is published to Maven Central.
        enabled.set(false)
    }
}
