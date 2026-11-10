plugins {
    id("io.micronaut.build.internal.bom")
}

micronautBuild {
    binaryCompatibility {
        // No version of this project has been released yet, so `findBaseline` has nothing to compare against.
        // Switch to `enabledAfter("<first release>")` once the first version is published to Maven Central.
        enabled.set(false)
    }
}
