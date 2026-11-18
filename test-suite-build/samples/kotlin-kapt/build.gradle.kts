plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.kapt")
    id("io.micronaut.library")
}

val graphqlToolsVersion = providers.gradleProperty("graphqlToolsVersion").get()

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        javaParameters.set(true)
    }
}

micronaut {
    version.set(providers.gradleProperty("micronautPlatformVersion"))
    processing {
        incremental(true)
        annotations("example.*")
    }
}

// tag::dependencies[]
dependencies {
    val graphqlToolsBom = platform("io.micronaut.graphql.tools:micronaut-graphql-tools-bom:$graphqlToolsVersion")
    implementation(graphqlToolsBom)
    kapt(graphqlToolsBom)
    implementation("io.micronaut.graphql.tools:micronaut-graphql-tools")
    kapt("io.micronaut.graphql.tools:micronaut-graphql-tools-processor")
}
// end::dependencies[]

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

// tag::schema-dirs[]
// The directories the compile-time checks read the schema from. Add a directory for schema that the application adds
// by code, so that the checks see it too.
val graphqlSchemaDirs = files("src/main/resources/graphql")

kapt {
    arguments {
        arg("micronaut.graphql.tools.processor.schema.dirs", graphqlSchemaDirs.files.joinToString(File.pathSeparator) { it.absolutePath })
    }
}

// kapt does not track the schema files by itself: declare them as an input of its tasks, so that a schema edit reruns them.
tasks.matching { it.name == "kaptKotlin" || it.name == "kaptGenerateStubsKotlin" }.configureEach {
    inputs.files(graphqlSchemaDirs).withPropertyName("graphqlSchemaDirs").withPathSensitivity(PathSensitivity.RELATIVE)
}
// end::schema-dirs[]

// tag::checks[]
// ./gradlew build -PgraphqlChecks=false compiles without the checks: the build prints one note, and the application still
// runs every check at startup.
kapt {
    arguments {
        arg("micronaut.graphql.tools.processor.checks", providers.gradleProperty("graphqlChecks").getOrElse("true"))
    }
}
// end::checks[]
