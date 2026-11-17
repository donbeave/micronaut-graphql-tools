plugins {
    id("java")
    id("me.champeau.jmh") version "0.7.4"
}

// Benchmarks of the request hot path and the allocation gates (D-02). Nothing here is published.
//   src/main                 the benchmarked schema, resolvers and operations, shared by everything below
//   build/generated/large-schema   the generated large schemas of 100, 1,600 and 10,000 fields and the classes they are bound to (generateLargeSchema)
//   src/jmh                  the JMH benchmarks: ./gradlew :benchmarks:jmh
//   src/jmhSmokeTest         jmhSmokeTest: the JMH benchmarks as a JUnit test (no fork, single shot), part of `check`
//   src/allocationTest       allocationTest: bytes per operation against allocation-baselines.properties, part of `check`

repositories {
    mavenCentral()
}

// Compiled with the JDK that runs the build (21 and 25 are the two full CI legs, D-32), like the test-suite-* modules; no toolchain, so
// the allocation baselines are recorded and checked on the same JDK that built them.
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

// the graphql-java leg by role (floor, default, ceiling, latest), selected as in the root build.gradle.kts
val graphqlJavaLeg = providers.gradleProperty("graphqlJavaLeg").getOrElse("default")

// Realistic large schemas for the start-up benchmarks: object types of 10 fields each (seven properties, a link to the
// next type, a method bound through a type resolver with an argument, and one more property), reached by a chain from
// the root, so that strict mode accepts them. The classes are generated so that there is no hand-written bulk. Each size
// is a package of its own (large.f<fields>) and is selected at run time by benchmarks.large.fields, so that the sizes
// never meet in one context.
val largeTypeCounts = listOf(10, 160, 1000) // 100, 1,600 and 10,000 fields
val generatedLargeSchema = layout.buildDirectory.dir("generated/large-schema")

val generateLargeSchema = tasks.register("generateLargeSchema") {
    val output = generatedLargeSchema
    val typeCounts = largeTypeCounts
    inputs.property("typeCounts", typeCounts)
    outputs.dir(output)
    doLast {
        val root = output.get().asFile
        root.deleteRecursively()
        val resourceDir = root.resolve("resources").also { it.mkdirs() }
        for (types in typeCounts) {
            val fields = types * 10
            val pkg = "io.micronaut.graphql.tools.benchmarks.large.f$fields"
            val javaDir = root.resolve("java/" + pkg.replace('.', '/')).also { it.mkdirs() }
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
                    |import io.micronaut.context.annotation.Requires;
                    |import io.micronaut.graphql.tools.annotation.GraphQLType;
                    |import java.util.List;
                    |
                    |@Requires(property = "benchmarks.large.fields", value = "$fields")
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
                    |@Requires(property = "benchmarks.large.fields", value = "$fields")
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
                |@Requires(property = "benchmarks.large.fields", value = "$fields")
                |@GraphQLRootResolver
                |public class LargeQuery {
                |    public Node0 root() { return new Node0(); }
                |}
                |""".trimMargin()
            )
            resourceDir.resolve("large-schema-$fields.graphqls").writeText(sdl.toString())
        }
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
    // Its own JVM per operation, with a heap that does not grow during a measurement. C1 only: without the escape
    // analysis of C2 the figure is every allocation the code performs, which does not depend on the JIT's choices, the
    // machine or the JDK patch level (C2 figures for one operation differed between two identical runs). JMH's gc
    // profiler reports the C2 steady state.
    forkEvery = 1
    maxParallelForks = 1
    minHeapSize = "512m"
    maxHeapSize = "512m"
    jvmArgs("-XX:+UseG1GC", "-XX:TieredStopAtLevel=1")
    systemProperty("allocation.baselines", allocationBaselines.asFile.absolutePath)
    systemProperty("allocation.graphqlJavaLeg", graphqlJavaLeg)
    systemProperty("allocation.update", providers.gradleProperty("allocation.update").isPresent)
    inputs.file(allocationBaselines).withPropertyName("allocationBaselines")
    outputs.upToDateWhen { false } // a measurement, never up to date
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
    // The 10,000-field schema is a chain of 1,000 types, and binding recurses once per type of the chain: the default
    // thread stack overflows (measured), so the JVM that runs it gets a larger one.
    jvmArgs("-Xss16m")
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(allocationTestTask, jmhSmokeTestTask)
}

// -Pjmh.profile=quick is a short run (one fork, a few seconds per benchmark) for a first look. The default profile is the
// measuring run: two forks, five warmup and five measured iterations of one second each.
val quickProfile = providers.gradleProperty("jmh.profile").orNull == "quick"

jmh {
    // The allocation figure is the gc profiler's gc.alloc.rate.norm, in bytes per operation.
    jmhVersion = libs.versions.jmh
    fork = if (quickProfile) 1 else 2
    warmupIterations = if (quickProfile) 2 else 5
    iterations = if (quickProfile) 3 else 5
    warmup = "1s"
    timeOnIteration = "1s"
    profilers = listOf("gc")
    resultFormat = "JSON"
    // the 10,000-field schema binds recursively along its chain of types (see jmhSmokeTest)
    jvmArgs = listOf("-Xss16m")
    includes = providers.gradleProperty("jmh.includes").map { listOf(it) }.orElse(listOf()).get()
}
