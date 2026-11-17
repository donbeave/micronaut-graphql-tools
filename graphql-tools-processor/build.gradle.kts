plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

dependencies {
    implementation(mnGraphql.graphql.java)

    testImplementation(projects.micronautGraphqlTools)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.inject.java)
    testRuntimeOnly("ch.qos.logback:logback-classic")
}

micronautBuild {
    // Java 21 until #144 deletes the JSR-269 `processing/` package: it uses Elements.getFileObjectOf (JDK 18+) and no
    // JDK 17 fallback is written for code that is going away. #144 moves this module to the default (release 17).
    javaVersion = 21
    binaryCompatibility {
        enabled.set(false)
    }
}
