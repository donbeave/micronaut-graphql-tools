plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

dependencies {
    api(mn.micronaut.jackson.databind) // TODO remove me pls, use micronaut serialization
    api(mnGraphql.micronaut.graphql)
    api(libs.managed.graphql.java.extended.scalars)

    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testAnnotationProcessor(mn.micronaut.inject.java) // Java test fixtures (beans, introspections)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(libs.jetbrains.annotations)
    testImplementation(libs.logback.classic)
    testImplementation(libs.graphql.java.datetime)
    testImplementation(libs.graphql.java.extended.validation)
}

micronautBuild {
    binaryCompatibility {
        // No version of this project has been released yet, so `findBaseline` has nothing to compare against.
        // Switch to `enabledAfter("<first release>")` once the first version is published to Maven Central.
        enabled.set(false)
    }
}
