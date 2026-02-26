plugins {
    id("java")
    id("io.micronaut.application")
    id("io.micronaut.aot")
    id("com.apollographql.apollo").version("4.4.1") // TODO
}

dependencies {
    implementation(projects.micronautGraphqlTools)
    implementation(libs.apollo.runtime)
    implementation(libs.apollo.rx3.support)
}

application {
    mainClass.set("example.Application")
}

graalvmNative.toolchainDetection.set(false)

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("example.*")
    }
    aot {
        optimizeServiceLoading.set(false)
        convertYamlToJava.set(false)
        precomputeOperations.set(true)
        cacheEnvironment.set(true)
        optimizeClassLoading.set(true)
        deduceEnvironment.set(true)
        optimizeNetty.set(true)
    }
}

apollo {
    service("service") {
        packageName.set("example.client")
        schemaFile.set(file("src/main/resources/schema.graphqls"))
        generateKotlinModels.set(false)
    }
}
