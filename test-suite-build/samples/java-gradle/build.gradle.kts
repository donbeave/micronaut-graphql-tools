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

// tag::dependencies[]
dependencies {
    val graphqlToolsBom = platform("io.micronaut.graphql.tools:micronaut-graphql-tools-bom:$graphqlToolsVersion")
    implementation(graphqlToolsBom)
    annotationProcessor(graphqlToolsBom)
    implementation("io.micronaut.graphql.tools:micronaut-graphql-tools")
    annotationProcessor("io.micronaut.graphql.tools:micronaut-graphql-tools-processor")
}
// end::dependencies[]

// tag::schema-dirs[]
// The directories the compile-time checks read the schema from. Add a directory for schema that the application adds
// by code, so that the checks see it too.
val graphqlSchemaDirs = files("src/main/resources/graphql")

abstract class GraphQLSchemaDirs : CommandLineArgumentProvider {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dirs: ConfigurableFileCollection

    override fun asArguments() = listOf("-Amicronaut.graphql.tools.processor.schema.dirs=" +
        dirs.files.joinToString(File.pathSeparator) { it.absolutePath })
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgumentProviders.add(objects.newInstance<GraphQLSchemaDirs>().apply { dirs.from(graphqlSchemaDirs) })
}
// end::schema-dirs[]

// tag::checks[]
// ./gradlew build -PgraphqlChecks=false compiles without the checks: the build prints one note, and the application still
// runs every check at startup.
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-Amicronaut.graphql.tools.processor.checks=${providers.gradleProperty("graphqlChecks").getOrElse("true")}")
}
// end::checks[]
