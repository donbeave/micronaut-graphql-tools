plugins {
    groovy
    id("io.micronaut.library")
}

val graphqlToolsVersion = providers.gradleProperty("graphqlToolsVersion").get()

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

micronaut {
    version.set(providers.gradleProperty("micronautPlatformVersion"))
}

// The Micronaut BOM manages a Groovy that cannot read the class files of the newest JDKs; this is not part of the wiring.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.groovy") {
            useVersion(providers.gradleProperty("groovyVersion").get())
        }
    }
}

// tag::dependencies[]
dependencies {
    val graphqlToolsBom = platform("io.micronaut.graphql.tools:micronaut-graphql-tools-bom:$graphqlToolsVersion")
    implementation(graphqlToolsBom)
    compileOnly(graphqlToolsBom)
    implementation("io.micronaut.graphql.tools:micronaut-graphql-tools")
    // The Groovy compiler loads the visitors from its compile classpath.
    compileOnly("io.micronaut.graphql.tools:micronaut-graphql-tools-processor")
}
// end::dependencies[]

dependencies {
    implementation("org.apache.groovy:groovy")
}

// tag::schema-dirs[]
// The directories the compile-time checks read the schema from. Add a directory for schema that the application adds
// by code, so that the checks see it too.
val graphqlSchemaDirs = files("src/main/resources/graphql")

tasks.named<GroovyCompile>("compileGroovy") {
    // The Groovy compiler reads its options from system properties, so it must run in its own JVM (the default).
    options.forkOptions.jvmArgs!!.add("-Dmicronaut.graphql.tools.processor.schema.dirs=" +
        graphqlSchemaDirs.files.joinToString(File.pathSeparator) { it.absolutePath })
    // Gradle does not track the schema files by itself: declare them as an input, so that a schema edit reruns the task.
    inputs.files(graphqlSchemaDirs).withPropertyName("graphqlSchemaDirs").withPathSensitivity(PathSensitivity.RELATIVE)
}
// end::schema-dirs[]

// tag::checks[]
// ./gradlew build -PgraphqlChecks=false compiles without the checks: the build prints one note, and the application still
// runs every check at startup.
tasks.named<GroovyCompile>("compileGroovy") {
    options.forkOptions.jvmArgs!!.add("-Dmicronaut.graphql.tools.processor.checks=${providers.gradleProperty("graphqlChecks").getOrElse("true")}")
}
// end::checks[]
