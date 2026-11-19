plugins {
    id("io.micronaut.library")
    application
}

val graphqlToolsVersion = providers.gradleProperty("graphqlToolsVersion").get()

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

micronaut {
    version.set(providers.gradleProperty("micronautPlatformVersion"))
}

application {
    mainClass.set("example.app.Application")
}

dependencies {
    implementation(platform("io.micronaut.graphql.tools:micronaut-graphql-tools-bom:$graphqlToolsVersion"))
    implementation("io.micronaut.graphql.tools:micronaut-graphql-tools")
    implementation(project(":books"))
    implementation(project(":authors"))
    // the schema is found on the classpath at startup; the application does not apply the processor
    runtimeOnly(project(":schema"))
}
