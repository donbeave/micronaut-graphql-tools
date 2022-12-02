plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

dependencies {
    api(libs.managed.graphql.java)
    api(libs.micronaut.jackson.databind)
    api(mn.micronaut.inject)

    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(libs.jetbrains.annotations)
}
