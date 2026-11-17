plugins {
    id("java")
    id("me.champeau.jmh") version "0.7.4"
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

dependencies {
    implementation(projects.micronautGraphqlTools)
    implementation(mn.micronaut.inject)
    annotationProcessor(mn.micronaut.inject.java)
    runtimeOnly(libs.logback.classic)

    jmh(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator.annprocess)
}

val graphqlJavaVersion = providers.gradleProperty("graphqlJavaVersion").getOrElse("21.3")

val largeTypes = 160
val generatedLargeSchema = layout.buildDirectory.dir("generated/large-schema")

val generateLargeSchema = tasks.register("generateLargeSchema") {
    val output = generatedLargeSchema
    val types = largeTypes
    inputs.property("types", types)
    outputs.dir(output)
    doLast {
        val root = output.get().asFile
        root.deleteRecursively()
        val pkg = "io.micronaut.graphql.tools.benchmarks.large"
        val javaDir = root.resolve("java/" + pkg.replace('.', '/')).also { it.mkdirs() }
        val resourceDir = root.resolve("resources").also { it.mkdirs() }
        val sdl = StringBuilder("schema {\n  query: LargeQuery\n}\n\ntype LargeQuery {\n  root: Node0\n}\n")
        for (i in 0 until types) {
            val next = (i + 1) % types
            sdl.append(
                """
                |
                |type Node$i {
                |  id: ID!
                |  name: String!
                |  count: Int!
                |  price: Float!
                |  active: Boolean!
                |  label: String
                |  note: String
                |  tags: [String!]!
                |  next: Node$next
                |  computed(n: Int!): String
                |}
                |""".trimMargin()
            )
            javaDir.resolve("Node$i.java").writeText(
                """
                |package $pkg;
                |
                |import io.micronaut.graphql.tools.annotation.GraphQLType;
                |import java.util.List;
                |
                |@GraphQLType
                |public class Node$i {
                |    public String getId() { return "id"; }
                |    public String getName() { return "name"; }
                |    public int getCount() { return 1; }
                |    public double getPrice() { return 1.5; }
                |    public boolean isActive() { return true; }
                |    public String getLabel() { return "label"; }
                |    public String getNote() { return "note"; }
                |    public List<String> getTags() { return List.of("tag"); }
                |    public Node$next getNext() { return null; }
                |}
                |""".trimMargin()
            )
            javaDir.resolve("Node${i}Resolver.java").writeText(
                """
                |package $pkg;
                |
                |import io.micronaut.context.annotation.Requires;
                |import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
                |
                |@Requires(property = "benchmarks.large", value = "true")
                |@GraphQLTypeResolver(Node$i.class)
                |public class Node${i}Resolver {
                |    public String computed(Node$i node, int n) { return "computed"; }
                |}
                |""".trimMargin()
            )
        }
        javaDir.resolve("LargeQuery.java").writeText(
            """
            |package $pkg;
            |
            |import io.micronaut.context.annotation.Requires;
            |import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
            |
            |@Requires(property = "benchmarks.large", value = "true")
            |@GraphQLRootResolver
            |public class LargeQuery {
            |    public Node0 root() { return new Node0(); }
            |}
            |""".trimMargin()
        )
        resourceDir.resolve("large-schema.graphqls").writeText(sdl.toString())
    }
}

sourceSets.main {
    java.srcDir(files(generatedLargeSchema.map { it.dir("java") }).builtBy(generateLargeSchema))
    resources.srcDir(files(generatedLargeSchema.map { it.dir("resources") }).builtBy(generateLargeSchema))
}

val allocationTest by sourceSets.creating
val jmhSmokeTest by sourceSets.creating

configurations {
    named(allocationTest.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
    named(allocationTest.runtimeOnlyConfigurationName) { extendsFrom(configurations.runtimeOnly.get()) }
    named(jmhSmokeTest.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
    named(jmhSmokeTest.runtimeOnlyConfigurationName) { extendsFrom(configurations.runtimeOnly.get()) }
}

dependencies {
    for (testSourceSet in listOf(allocationTest, jmhSmokeTest)) {
        add(testSourceSet.implementationConfigurationName, platform(libs.junit.bom))
        add(testSourceSet.implementationConfigurationName, "org.junit.jupiter:junit-jupiter")
        add(testSourceSet.implementationConfigurationName, sourceSets.main.get().output)
        add(testSourceSet.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")
    }
    add(jmhSmokeTest.implementationConfigurationName, libs.jmh.core)
}

val allocationBaselines = layout.projectDirectory.file("allocation-baselines.properties")

val allocationTestTask = tasks.register<Test>("allocationTest") {
    group = "verification"
    description = "Fails when an operation of the request hot path allocates more than its baseline."
    testClassesDirs = allocationTest.output.classesDirs
    classpath = allocationTest.runtimeClasspath
    useJUnitPlatform()
    forkEvery = 1
    maxParallelForks = 1
    minHeapSize = "512m"
    maxHeapSize = "512m"
    jvmArgs("-XX:+UseG1GC", "-XX:TieredStopAtLevel=1")
    systemProperty("allocation.baselines", allocationBaselines.asFile.absolutePath)
    systemProperty("allocation.graphqlJavaVersion", graphqlJavaVersion)
    systemProperty("allocation.update", providers.gradleProperty("allocation.update").isPresent)
    inputs.file(allocationBaselines).withPropertyName("allocationBaselines")
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

val jmhSmokeTestTask = tasks.register<Test>("jmhSmokeTest") {
    group = "verification"
    description = "Runs every JMH benchmark once in this JVM, so that they compile and run on every check."
    testClassesDirs = jmhSmokeTest.output.classesDirs
    classpath = jmhSmokeTest.runtimeClasspath + sourceSets["jmh"].output + sourceSets["jmh"].runtimeClasspath +
            files(tasks.named("jmhCompileGeneratedClasses"))
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(allocationTestTask, jmhSmokeTestTask)
}

val quickProfile = providers.gradleProperty("jmh.profile").orNull == "quick"

jmh {
    jmhVersion = libs.versions.jmh
    fork = if (quickProfile) 1 else 2
    warmupIterations = if (quickProfile) 2 else 5
    iterations = if (quickProfile) 3 else 5
    warmup = "1s"
    timeOnIteration = "1s"
    profilers = listOf("gc")
    resultFormat = "JSON"
    includes = providers.gradleProperty("jmh.includes").map { listOf(it) }.orElse(listOf()).get()
}
