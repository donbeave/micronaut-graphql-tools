plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

dependencies {
    api(mn.micronaut.jackson.databind) // TODO remove me pls, use micronaut serialization
    api(mnGraphql.micronaut.graphql)
    api(libs.managed.graphql.java.extended.scalars)

    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(libs.jetbrains.annotations)
}
