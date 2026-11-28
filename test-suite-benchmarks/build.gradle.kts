plugins {
    id("io.micronaut.build.internal.graphql-tools-test-module")
    id("me.champeau.jmh") version "0.7.4"
}

dependencies {
    implementation(platform(libs.micronaut.platform))
    implementation(projects.micronautGraphqlTools)
    // the coercions of the standard scalars (#46) are benchmarked next to the argument conversion
    implementation(projects.micronautGraphqlToolsScalars)
    // a request with the metrics and tracing modules on and no field traced is gated against the same request without them (#24)
    implementation(projects.micronautGraphqlToolsMicrometer)
    implementation(projects.micronautGraphqlToolsOpentelemetry)
    implementation(mnMicrometer.micronaut.micrometer.core)
    // an unsecured field with the security module installed is gated against the same field without it (#595); the module has
    // Micronaut Security as compile-only, so the application, here the benchmarks, supplies it
    implementation(projects.micronautGraphqlToolsSecurity)
    // the codec of the client (#684) is gated beside the server cases; it runs on Micronaut Serialization, which the benchmarks bring
    // here and the other cases do not use
    implementation(projects.micronautGraphqlToolsClient)
    implementation(mnSerde.micronaut.serde.jackson)
    // the loopback cases (#686) call an embedded server through the client and through a plain HTTP client
    implementation(mn.micronaut.http.server.netty)
    implementation(mn.micronaut.http.client)
    annotationProcessor(platform(libs.micronaut.platform))
    annotationProcessor(mnSerde.micronaut.serde.processor)
    implementation(mnSecurity.micronaut.security)
    implementation(mnTracing.micronaut.tracing.opentelemetry)
    implementation(mn.micronaut.inject)
    // the per-class binding rules in the form the compiler runs them (#417): the processor's element adapter over compiler class elements,
    // compiled in process; no part of it reaches the library's runtime API
    implementation(projects.micronautGraphqlToolsProcessor)
    implementation(projects.micronautGraphqlToolsBinding)
    implementation(mn.micronaut.inject.java)
    annotationProcessor(mn.micronaut.inject.java)
    runtimeOnly(mnLogging.logback.classic)

    jmh(libs.jmh.core)
    // the jmh plugin resolves jmh-generator-asm's asm 9.0 on its own, ahead of the platform's: the in-process compilation of the binding
    // rules fixture (#417) runs Micronaut's processors in the benchmark JVM, and they need the platform's ASM
    jmh(platform(libs.micronaut.platform))
    jmh("org.ow2.asm:asm")
    jmhAnnotationProcessor(libs.jmh.generator.annprocess)
}

val graphqlJavaLeg = providers.gradleProperty("graphqlJavaLeg").getOrElse("default")

// Each size is a package of its own (large.f<fields>), selected by benchmarks.large.fields. The benchmark jar generates
// the 1,600-field schema, the one the other start-up benchmarks bind. -PlargeFields=<fields> generates another size
// instead, so that the benchmark JVM of that size has no other generated bean on its classpath (scripts/startup-report.py
// builds one jar per size). jmhSmokeTest needs the 100 and 10,000-field schemas as well: they are generated into its
// own source set.
//
// -PlargeShape=interfaces generates the interface-heavy schema (#368) of that size instead of the rings (3,000 fields
// when -PlargeFields is not given); the jmhSmokeTest source set has both shapes at its two sizes.
val largeShape = providers.gradleProperty("largeShape").getOrElse("rings")
require(largeShape == "rings" || largeShape == "interfaces") { "-PlargeShape must be rings or interfaces, not $largeShape" }
val interfaceShape = largeShape == "interfaces"
val largeTypeCounts = providers.gradleProperty("largeFields").map { listOf(it.toInt() / 10) }
    .orElse(listOf(if (interfaceShape) 300 else 160)).get()
val smokeTypeCounts = listOf(10, 1000)
// -PlargeUnreturnedShared=true leaves the root field `shared` out of the interface-heavy schema, so the interface Shared, which
// every object type implements, is one that no field returns (#71): its class comes from the dictionary lookup, not from a field.
val largeUnreturnedShared = providers.gradleProperty("largeUnreturnedShared").map { it.toBoolean() }.getOrElse(false)
require(!largeUnreturnedShared || interfaceShape) { "-PlargeUnreturnedShared needs -PlargeShape=interfaces" }
// The types form rings of at most this many types, each reached from a root field of its own. graphql-java builds the
// executable schema with one recursive call per type of a chain, so one ring of a thousand types overflows the default
// thread stack of a cold JVM (on CI); rings of this length leave the benchmark JVMs on the default stack.
val ringLength = 100
val generatedLargeSchema = layout.buildDirectory.dir("generated/large-schema")
val generatedSmokeSchema = layout.buildDirectory.dir("generated/smoke-schema")
val generatedSmokeInterfaceSchema = layout.buildDirectory.dir("generated/smoke-interface-schema")

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
                    |import jakarta.inject.Singleton;
                    |
                    |@Requires(property = "benchmarks.large.fields", value = "$fields")
                    |@Singleton
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
                |import jakarta.inject.Singleton;
                |
                |@Requires(property = "benchmarks.large.fields", value = "$fields")
                |@Singleton
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

// The interface-heavy schema: fields / 10 object types of 10 fields, in groups of ten types. Every type implements the
// interface Shared and the interface Group<g> of its group, which extends Shared, so the interfaces declare shared fields
// and a field is declared by two interfaces. The group's types are linked in a ring through next, each group is reached
// from a root field returning a list of its interface, and the type-mapping dictionary has an entry for the interface Shared (the
// only way its class is found) and for every object type: a quarter of them map to a Java interface, the rest to a class.
fun registerInterfaceSchema(name: String, output: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
                            typeCounts: List<Int>, unreturnedShared: Boolean = false) = tasks.register(name) {
    inputs.property("typeCounts", typeCounts)
    inputs.property("unreturnedShared", unreturnedShared)
    outputs.dir(output)
    doLast {
        val root = output.get().asFile
        root.deleteRecursively()
        val resourceDir = root.resolve("resources").also { it.mkdirs() }
        for (types in typeCounts) {
            val fields = types * 10
            val pkg = "io.micronaut.graphql.tools.benchmarks.large.i$fields"
            val javaDir = root.resolve("java/" + pkg.replace('.', '/')).also { it.mkdirs() }
            val groups = maxOf(1, types / 10)
            val requires = "@Requires(property = \"benchmarks.interface.fields\", value = \"$fields\")"
            // an object type maps to its interface when its number is a multiple of four
            fun javaType(i: Int) = if (i % 4 == 0) "Obj${i}Api" else "Obj$i"
            fun write(file: String, body: String) = javaDir.resolve("$file.java").writeText(body.trimMargin())
            val sdl = StringBuilder("schema {\n  query: LargeQuery\n}\n\ntype LargeQuery {\n")
            if (!unreturnedShared) {
                sdl.append("  shared: [Shared!]!\n")
            }
            for (g in 0 until groups) {
                sdl.append("  group$g: [Group$g!]!\n")
            }
            sdl.append("}\n\ninterface Shared {\n  id: ID!\n  name: String!\n}\n")
            write("Shared", """
                |package $pkg;
                |
                |public interface Shared {
                |    String getId();
                |    String getName();
                |}
                |""")
            for (g in 0 until groups) {
                sdl.append("\ninterface Group$g implements Shared {\n  id: ID!\n  name: String!\n  count: Int!\n  label: String\n}\n")
                write("Group$g", """
                    |package $pkg;
                    |
                    |public interface Group$g extends Shared {
                    |    int getCount();
                    |    String getLabel();
                    |}
                    |""")
            }
            // Shared has no introspection, so the dictionary is the only way its class is found: without this entry the lookup of
            // an interface that no field returns finds no class and skips the resolver-method search (LargeSchema.checkSharedMapped asserts it)
            val dictionary = StringBuilder("\n                |            dictionary.registerType(\"Shared\", Shared.class);")
            for (i in 0 until types) {
                val g = i % groups
                val next = (i + groups) % types
                val self = javaType(i)
                sdl.append(
                    """
                    |
                    |type Obj$i implements Group$g & Shared {
                    |  id: ID!
                    |  name: String!
                    |  count: Int!
                    |  label: String
                    |  price: Float!
                    |  active: Boolean!
                    |  note: String
                    |  tags: [String!]!
                    |  next: Obj$next
                    |  extra: String
                    |}
                    |""".trimMargin()
                )
                val getters = """
                    |    public String getId() { return "id"; }
                    |    public String getName() { return "name"; }
                    |    public int getCount() { return 1; }
                    |    public String getLabel() { return "label"; }
                    |    public double getPrice() { return 1.5; }
                    |    public boolean isActive() { return true; }
                    |    public String getNote() { return "note"; }
                    |    public java.util.List<String> getTags() { return java.util.List.of("tag"); }
                    |    public ${javaType(next)} getNext() { return null; }
                    |    public String getExtra() { return "extra"; }"""
                if (i % 4 == 0) {
                    write("Obj${i}Api", """
                        |package $pkg;
                        |
                        |import io.micronaut.graphql.tools.annotation.GraphQLType;
                        |
                        |@GraphQLType
                        |public interface Obj${i}Api extends Group$g {
                        |    double getPrice();
                        |    boolean isActive();
                        |    String getNote();
                        |    java.util.List<String> getTags();
                        |    ${javaType(next)} getNext();
                        |    String getExtra();
                        |}
                        |""")
                }
                val implemented = if (i % 4 == 0) "Obj${i}Api" else "Group$g"
                val mapping = if (i % 4 == 0) "@GraphQLType(Obj${i}Api.class)" else "@GraphQLType"
                write("Obj$i", """
                    |package $pkg;
                    |
                    |import io.micronaut.context.annotation.Requires;
                    |import io.micronaut.graphql.tools.annotation.GraphQLType;
                    |
                    |$requires
                    |$mapping
                    |public class Obj$i implements $implemented {
                    |$getters
                    |}
                    |""")
                dictionary.append("\n                |            dictionary.registerType(\"Obj$i\", $self.class);")
            }
            write("LargeDictionary", """
                |package $pkg;
                |
                |import io.micronaut.context.annotation.Bean;
                |import io.micronaut.context.annotation.Factory;
                |import io.micronaut.context.annotation.Requires;
                |import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer;
                |import jakarta.inject.Singleton;
                |
                |$requires
                |@Factory
                |public class LargeDictionary {
                |
                |    @Bean
                |    @Singleton
                |    GraphQLSchemaMappingDictionaryCustomizer largeTypes() {
                |        // statements, not one chained call: javac overflows its stack on a chain of two thousand calls
                |        return dictionary -> {$dictionary
                |        };
                |    }
                |}
                |""")
            val roots = StringBuilder(if (unreturnedShared) "" else "|    public java.util.List<Shared> shared() { return java.util.List.of(); }\n")
            for (g in 0 until groups) {
                roots.append("|    public java.util.List<Group$g> group$g() { return java.util.List.of(); }\n")
            }
            write("LargeQuery", """
                |package $pkg;
                |
                |import io.micronaut.context.annotation.Requires;
                |import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
                |import jakarta.inject.Singleton;
                |
                |$requires
                |@Singleton
                |@GraphQLRootResolver
                |public class LargeQuery {
$roots|}
                |""")
            resourceDir.resolve("interface-schema-$fields.graphqls").writeText(sdl.toString())
            if (unreturnedShared) {
                // no resolver bean binds a field of Shared, so the strict check reports its dictionary entry as unused: accept that one
                // finding (the returned build binds Shared and has none), instead of switching the check off for the whole run
                resourceDir.resolve("application.properties").writeText(
                    "micronaut.graphql.tools.allow.findings=!unused-dictionary-entry Shared = $pkg.Shared\n")
            }
        }
    }
}
// The cold start of the client (#686): 10 clients of 20 operations each, declarative (@GraphQLClient) or plain (@Client with @Post), one
// mode per build, so that the JVM of a mode has the other one's bean definitions nowhere (-PcoldStartMode=declarative|plain; the default,
// none, generates no client and leaves the other cases as they were). scripts/client-coldstart-ab.py builds one jar per mode.
val coldStartMode = providers.gradleProperty("coldStartMode").getOrElse("none")
require(coldStartMode in listOf("none", "declarative", "plain")) { "-PcoldStartMode must be none, declarative or plain, not $coldStartMode" }
val coldStartClients = 10
val coldStartOperations = 20
val generatedColdStart = layout.buildDirectory.dir("generated/cold-start")

val generateColdStartClients = tasks.register("generateColdStartClients") {
    inputs.property("mode", coldStartMode)
    outputs.dir(generatedColdStart)
    doLast {
        val root = generatedColdStart.get().asFile
        root.deleteRecursively()
        val pkg = "io.micronaut.graphql.tools.benchmarks.coldstart"
        val javaDir = root.resolve("java/" + pkg.replace('.', '/')).also { it.mkdirs() }
        val count = if (coldStartMode == "none") 0 else coldStartClients
        for (c in 0 until count) {
            val operations = (0 until coldStartOperations).joinToString("\n\n") { o ->
                if (coldStartMode == "declarative") {
                    val document = "query Op${c}_$o(\$id: ID!) { book(id: \$id) { id title pages tags } }"
                    """
                    |    @GraphQLOperation("$document")
                    |    @GraphQLOperationMetadata(document = "$document", operationName = "Op${c}_$o", variables = "id", payload = "field")
                    |    ClientBook op$o(String id);
                    """.trimMargin()
                } else {
                    """
                    |    @Post("/graphql$o")
                    |    RawResponse op$o(@Body RawRequest request);
                    """.trimMargin()
                }
            }
            val header = if (coldStartMode == "declarative") {
                """
                |import io.micronaut.graphql.tools.benchmarks.Fixtures.ClientBook;
                |import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
                |import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;
                |import io.micronaut.graphql.tools.client.internal.GraphQLOperationMetadata;
                |
                |@GraphQLClient(id = ColdStartSuite.SERVICE, endpoint = "/coldstart/graphql")
                """.trimMargin()
            } else {
                """
                |import io.micronaut.graphql.tools.benchmarks.loopback.LoopbackModels.RawRequest;
                |import io.micronaut.graphql.tools.benchmarks.loopback.LoopbackModels.RawResponse;
                |import io.micronaut.http.annotation.Body;
                |import io.micronaut.http.annotation.Post;
                |import io.micronaut.http.client.annotation.Client;
                |
                |@Client(id = ColdStartSuite.SERVICE, path = "/coldstart")
                """.trimMargin()
            }
            javaDir.resolve("ColdClient$c.java").writeText(
                "package $pkg;\n\n$header\npublic interface ColdClient$c {\n\n$operations\n}\n")
        }
        val resolves = (0 until count).joinToString("\n") { "        context.getBean(ColdClient$it.class);" }
        javaDir.resolve("ColdStartSuite.java").writeText(
            """
            |package $pkg;
            |
            |import io.micronaut.context.BeanContext;
            |
            |/** Generated by generateColdStartClients: the mode of this build and its clients. */
            |public final class ColdStartSuite {
            |
            |    public static final String MODE = "$coldStartMode";
            |    public static final String SERVICE = "coldstart";
            |    public static final int CLIENTS = $count;
            |    public static final int OPERATIONS = ${if (count == 0) 0 else coldStartOperations};
            |
            |    private ColdStartSuite() {
            |    }
            |
            |    static int resolve(BeanContext context) {
$resolves
            |        return CLIENTS;
            |    }
            |}
            |""".trimMargin()
        )
    }
}

val generateLargeSchema = if (interfaceShape) {
    registerInterfaceSchema("generateLargeSchema", generatedLargeSchema, largeTypeCounts, largeUnreturnedShared)
} else {
    registerLargeSchema("generateLargeSchema", generatedLargeSchema, largeTypeCounts)
}
val generateSmokeSchema = registerLargeSchema("generateSmokeSchema", generatedSmokeSchema, smokeTypeCounts)
val generateSmokeInterfaceSchema = registerInterfaceSchema("generateSmokeInterfaceSchema", generatedSmokeInterfaceSchema, smokeTypeCounts)

sourceSets.main {
    java.srcDir(files(generatedLargeSchema.map { it.dir("java") }).builtBy(generateLargeSchema))
    resources.srcDir(files(generatedLargeSchema.map { it.dir("resources") }).builtBy(generateLargeSchema))
    java.srcDir(files(generatedColdStart.map { it.dir("java") }).builtBy(generateColdStartClients))
}

// The native image of the cold-start entry point (#686): its size and its start time, per mode. Needs a GraalVM JDK (native-image) as the
// JDK of the build, and one mode per build: ./gradlew :test-suite-benchmarks:coldStartNativeImage -PcoldStartMode=plain|declarative.
// scripts/client-coldstart-native.py runs both and prints the sizes.
val nativeImage = File(System.getenv("JAVA_HOME") ?: System.getProperty("java.home"), "bin/native-image")
val coldStartNativeImage = tasks.register<Exec>("coldStartNativeImage") {
    group = "verification"
    description = "Builds a native image of the cold-start entry point of the selected -PcoldStartMode and prints its size."
    dependsOn(tasks.named("classes"))
    val binary = layout.buildDirectory.file("native/coldstart-$coldStartMode")
    outputs.file(binary)
    executable = nativeImage.path.toString()
    // Logback is left out: its logger is initialised at run time and an image without a binding logs through the no-op slf4j logger
    args("-cp", sourceSets.main.get().runtimeClasspath.filter { !it.name.startsWith("logback-") }.asPath, "--no-fallback", "--initialize-at-run-time=io.netty.handler.pcap", "-o", binary.get().asFile.path,
        "io.micronaut.graphql.tools.benchmarks.coldstart.ColdStartMain")
    doFirst {
        require(coldStartMode != "none") { "coldStartNativeImage needs -PcoldStartMode=declarative or plain" }
        require(nativeImage.exists()) { "No native-image at $nativeImage: set JAVA_HOME to a GraalVM JDK" }
        binary.get().asFile.parentFile.mkdirs()
    }
    doLast { println("coldstart-$coldStartMode: ${binary.get().asFile.length()} bytes") }
}

val allocationTest by sourceSets.creating
val jmhSmokeTest by sourceSets.creating

jmhSmokeTest.java.srcDir(files(generatedSmokeSchema.map { it.dir("java") }).builtBy(generateSmokeSchema))
jmhSmokeTest.resources.srcDir(files(generatedSmokeSchema.map { it.dir("resources") }).builtBy(generateSmokeSchema))
jmhSmokeTest.java.srcDir(files(generatedSmokeInterfaceSchema.map { it.dir("java") }).builtBy(generateSmokeInterfaceSchema))
jmhSmokeTest.resources.srcDir(files(generatedSmokeInterfaceSchema.map { it.dir("resources") }).builtBy(generateSmokeInterfaceSchema))

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
    // perf-nightly.yml reads this file: name it here, so that the format and the file name cannot drift apart.
    resultsFile = layout.buildDirectory.file("results/jmh/results.json")
    // The start-up benchmarks are measured cold, one fresh JVM per sample: the first two by scripts/startup-report.py, the
    // context start and first request ones by the benchmark jar. The warm-up of this task would start contexts in the same JVM first.
    excludes = listOf("BindingBenchmark", "RetainedHeapBenchmark", "ContextStartBenchmark", "FirstRequestBenchmark", "StartAndFirstRequestBenchmark", "ClientColdStartBenchmark")
    // The jar does the same by itself: its main class leaves them out unless a benchmark is named
    // (BenchmarkJarMain, which jmhJar below makes the Main-Class).
    includes = providers.gradleProperty("jmh.includes").map { listOf(it) }.orElse(listOf()).get()
}

tasks.named<Jar>("jmhJar") {
    manifest {
        attributes["Main-Class"] = "io.micronaut.graphql.tools.benchmarks.BenchmarkJarMain"
    }
}
