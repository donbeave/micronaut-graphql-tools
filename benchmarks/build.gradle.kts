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
    implementation(platform(libs.micronaut.platform))
    implementation(projects.micronautGraphqlTools)
    implementation(mn.micronaut.inject)
    annotationProcessor(mn.micronaut.inject.java)
    runtimeOnly("ch.qos.logback:logback-classic")

    jmh(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator.annprocess)
}

val graphqlJavaLeg = providers.gradleProperty("graphqlJavaLeg").getOrElse("default")

// Each size is a package of its own (large.f<fields>), selected by benchmarks.large.fields. The benchmark jar generates
// the 1,600-field schema, the one the other start-up benchmarks bind. -PlargeFields=<fields> generates another size
// instead, so that the benchmark JVM of that size has no other generated bean on its classpath (scripts/startup-report.py
// builds one jar per size). jmhSmokeTest needs the 100 and 10,000-field schemas as well: they are generated into its
// own source set.
val largeTypeCounts = providers.gradleProperty("largeFields").map { listOf(it.toInt() / 10) }.orElse(listOf(160)).get()
val smokeTypeCounts = listOf(10, 1000)
// The types form rings of at most this many types, each reached from a root field of its own. graphql-java builds the
// executable schema with one recursive call per type of a chain, so one ring of a thousand types overflows the default
// thread stack of a cold JVM (on CI); rings of this length leave the benchmark JVMs on the default stack.
val ringLength = 100
val generatedLargeSchema = layout.buildDirectory.dir("generated/large-schema")
val generatedSmokeSchema = layout.buildDirectory.dir("generated/smoke-schema")

fun registerLargeSchema(name: String, output: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
                        typeCounts: List<Int>) = tasks.register(name) {
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
            val rings = (types + ringLength - 1) / ringLength
            val sdl = StringBuilder("schema {\n  query: LargeQuery\n}\n\ntype LargeQuery {\n  root: Node0\n")
            for (ring in 1 until rings) {
                sdl.append("  root$ring: Node${ring * ringLength}\n")
            }
            sdl.append("}\n")
            for (i in 0 until types) {
                val ringStart = i / ringLength * ringLength
                val next = if (i + 1 < minOf(ringStart + ringLength, types)) i + 1 else ringStart
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
            val ringRoots = (1 until rings).joinToString("\n") {
                "                |    public Node${it * ringLength} root$it() { return new Node${it * ringLength}(); }"
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
$ringRoots
                |}
                |""".trimMargin()
            )
            resourceDir.resolve("large-schema-$fields.graphqls").writeText(sdl.toString())
        }
    }
}
val generateLargeSchema = registerLargeSchema("generateLargeSchema", generatedLargeSchema, largeTypeCounts)
val generateSmokeSchema = registerLargeSchema("generateSmokeSchema", generatedSmokeSchema, smokeTypeCounts)

sourceSets.main {
    java.srcDir(files(generatedLargeSchema.map { it.dir("java") }).builtBy(generateLargeSchema))
    resources.srcDir(files(generatedLargeSchema.map { it.dir("resources") }).builtBy(generateLargeSchema))
}

val allocationTest by sourceSets.creating
val jmhSmokeTest by sourceSets.creating

jmhSmokeTest.java.srcDir(files(generatedSmokeSchema.map { it.dir("java") }).builtBy(generateSmokeSchema))
jmhSmokeTest.resources.srcDir(files(generatedSmokeSchema.map { it.dir("resources") }).builtBy(generateSmokeSchema))

configurations {
    named(allocationTest.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
    named(allocationTest.runtimeOnlyConfigurationName) { extendsFrom(configurations.runtimeOnly.get()) }
    named(jmhSmokeTest.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
    named(jmhSmokeTest.runtimeOnlyConfigurationName) { extendsFrom(configurations.runtimeOnly.get()) }
}

dependencies {
    for (testSourceSet in listOf(allocationTest, jmhSmokeTest)) {
        add(testSourceSet.implementationConfigurationName, "org.junit.jupiter:junit-jupiter")
        add(testSourceSet.implementationConfigurationName, sourceSets.main.get().output)
        add(testSourceSet.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")
    }
    add(jmhSmokeTest.implementationConfigurationName, libs.jmh.core)
    add(jmhSmokeTest.annotationProcessorConfigurationName, mn.micronaut.inject.java)
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
    val update = providers.gradleProperty("allocation.update").isPresent
    // Relative to the working directory (the project directory): an absolute path would be a task input and differ per machine.
    systemProperty("allocation.baselines", allocationBaselines.asFile.name)
    systemProperty("allocation.graphqlJavaLeg", graphqlJavaLeg)
    systemProperty("allocation.update", update)
    inputs.file(allocationBaselines).withPropertyName("allocationBaselines").withPathSensitivity(PathSensitivity.NONE)
    // An update run rewrites its own input, so it must run every time and never be restored from the cache.
    outputs.upToDateWhen { !update }
    outputs.doNotCacheIf("the baselines are being updated") { update }
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
    // JMH locks jmh.lock in java.io.tmpdir, so builds on one machine collide there. The JVM gets a temp directory of
    // its own under this project's build directory, which only this build uses. The path is relative to the test's
    // working directory (this project), so it is the same on every machine.
    val jmhTempDir = layout.buildDirectory.dir("tmp/jmhSmokeTest")
    systemProperty("java.io.tmpdir", "build/tmp/jmhSmokeTest")
    doFirst { jmhTempDir.get().asFile.mkdirs() }
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
    // The start-up benchmarks are measured cold, one fresh JVM per sample, by scripts/startup-report.py. The warm-up of
    // this task would start contexts in the same JVM first.
    excludes = listOf("BindingBenchmark", "RetainedHeapBenchmark")
    // The jar does the same by itself: its main class leaves them out unless a benchmark is named
    // (BenchmarkJarMain, which jmhJar below makes the Main-Class).
    includes = providers.gradleProperty("jmh.includes").map { listOf(it) }.orElse(listOf()).get()
}

tasks.named<Jar>("jmhJar") {
    manifest {
        attributes["Main-Class"] = "io.micronaut.graphql.tools.benchmarks.BenchmarkJarMain"
    }
}
