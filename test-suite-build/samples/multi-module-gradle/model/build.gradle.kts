plugins {
    id("io.micronaut.library")
}

val graphqlToolsVersion = providers.gradleProperty("graphqlToolsVersion").get()

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

micronaut {
    version.set(providers.gradleProperty("micronautPlatformVersion"))
    processing {
        incremental(true)
        annotations("example.*")
    }
}

// The model classes carry the library's annotations and no resolver, so this module does not apply the processor.
dependencies {
    implementation(platform("io.micronaut.graphql.tools:micronaut-graphql-tools-bom:$graphqlToolsVersion"))
    api("io.micronaut.graphql.tools:micronaut-graphql-tools")
}
