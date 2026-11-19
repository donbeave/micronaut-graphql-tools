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

dependencies {
    val graphqlToolsBom = platform("io.micronaut.graphql.tools:micronaut-graphql-tools-bom:$graphqlToolsVersion")
    implementation(graphqlToolsBom)
    annotationProcessor(graphqlToolsBom)
    implementation("io.micronaut.graphql.tools:micronaut-graphql-tools")
    annotationProcessor("io.micronaut.graphql.tools:micronaut-graphql-tools-processor")
    implementation(project(":model"))
}

// tag::schema-dirs[]
// Every module that holds resolver classes applies the processor and reads the schema of the module that holds it. A
// module without resolvers does not apply the processor, so a schema edit does not recompile it. What the compile-time
// checks cannot see (a field that no resolver of any module binds) is checked when the application starts.
val graphqlSchemaDirs = files(project(":schema").layout.projectDirectory.dir("src/main/resources/graphql"))

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

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-Amicronaut.graphql.tools.processor.checks=${providers.gradleProperty("graphqlChecks").getOrElse("true")}")
}
