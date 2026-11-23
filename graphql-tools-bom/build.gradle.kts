plugins {
    id("io.micronaut.build.internal.graphql-tools-bom")
}

micronautBom {
    extraExcludedProjects.addAll("test-suite-*")
}
