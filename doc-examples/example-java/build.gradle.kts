plugins {
    id("java")
    id("io.micronaut.application")
    id("com.apollographql.apollo3").version("3.4.0") // TODO
}

application {
    mainClass.set("example.Application")
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
}

dependencies {
    implementation(projects.graphqlTools)
    implementation(libs.micronaut.graphql)
    implementation(libs.apollo.runtime)
    implementation(libs.apollo.rx3.support)
    runtimeOnly(libs.logback.classic)
}

apollo {
    packageName.set("example.client")
    schemaFile.set(file("src/main/resources/schema.graphqls"))
    generateKotlinModels.set(false)
}
