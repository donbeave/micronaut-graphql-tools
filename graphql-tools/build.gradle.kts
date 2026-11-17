plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

dependencies {
    api(mn.micronaut.jackson.databind)
    api(mnGraphql.micronaut.graphql)
    api(libs.managed.graphql.java.extended.scalars)

    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(libs.jetbrains.annotations)
    testImplementation(libs.logback.classic)
    testImplementation(libs.graphql.java.datetime)
    testImplementation(libs.graphql.java.extended.validation)
}

micronautBuild {
    binaryCompatibility {
        enabled.set(false)
    }
}
