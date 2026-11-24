plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

dependencies {
    // the schema rules shared with the runtime (SchemaSources): the compile-time checks read the same files
    implementation(projects.micronautGraphqlToolsBinding)
    implementation(mnGraphql.graphql.java)
    // the visitor API: the processor path of a build brings its own copy
    compileOnly(mn.micronaut.core.processor)
    // the javac elements, for the one question that only they answer (JavacElements); loaded only under javac
    compileOnly(mn.micronaut.inject.java)

    testImplementation(projects.micronautGraphqlTools)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.inject.java)
    // the single-value return types of the binding corpus (#428); the platform manages the versions
    testImplementation(platform(libs.micronaut.platform))
    testImplementation("io.reactivex.rxjava3:rxjava")
    testImplementation("io.reactivex.rxjava2:rxjava")
    // the corpus compiles Groovy cases in process, and Micronaut's AST transformation writes their bean definitions
    testImplementation(mn.micronaut.inject.groovy)
    // the Kotlin side of the corpus compiles its cases in a batched TestKit build
    testImplementation(gradleTestKit())
    testRuntimeOnly("ch.qos.logback:logback-classic")
    // the Kotlin cases of the corpus with a suspend resolver start up with the library's suspend support, which needs these
    testRuntimeOnly(mn.kotlin.stdlib)
    testRuntimeOnly(mn.kotlinx.coroutines.core)
}

// The binding corpus (#142): the cases are data under src/test/resources/corpus, read from the project directory.
// `-Pcorpus.update` (task corpusUpdate) rewrites the expected files instead of comparing; the diff is the review.
val corpusDir = layout.projectDirectory.dir("src/test/resources/corpus")
tasks.withType<Test>().configureEach {
    inputs.files(corpusDir).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("corpus")
    // The corpus compiles about 300 units in process and starts the application of each in a class loader of its own, in one JVM. Two
    // retentions used to keep every unit's loader (about 14 MB of metaspace and 6 MB of heap per unit), found with a heap dump:
    // - Groovy keeps its ClassInfo of a class in a java.lang.ClassValue, and a ClassValue stores the value in the Class it is asked
    //   about. The Groovy copy of a unit's loader asks about JDK classes (Integer, String), which live as long as the JVM, so their
    //   ClassValue maps held the ClassInfo of every unit (and through it the loader). Without ClassValue, Groovy keeps the ClassInfo in
    //   weak references.
    // - the JDK (the java.beans introspector cache, the method handle adapter caches) and Micronaut cache per class in soft
    //   references, and the JVM keeps a soft reference while the heap has room, however much metaspace it pins through the loader;
    //   a soft reference cleared at every collection lets the loaders go with the units.
    systemProperty("groovy.use.classvalue", "false")
    jvmArgs("-XX:SoftRefLRUPolicyMSPerMB=0")
    // the Kotlin batch is its own task (corpusKotlin): it needs the network and has its own budget
    systemProperty("corpus.dir", corpusDir.asFile.absolutePath)
    // the error reference page, whose example messages ErrorReferenceSpec compares with the startup of a corpus case
    val errorReference = rootProject.file("src/main/docs/guide/exceptions.adoc")
    inputs.file(errorReference).withPathSensitivity(PathSensitivity.NONE).withPropertyName("errorReference")
    systemProperty("guide.errors", errorReference.absolutePath)
    systemProperty("corpus.report", layout.buildDirectory.file("corpus/timings.txt").get().asFile.absolutePath)
    if (project.hasProperty("corpus.update")) {
        systemProperty("corpus.update", "true")
    }
    // `-Pcorpus.only=<regex>` runs only the cases whose name matches (for writing a case; not for CI)
    providers.gradleProperty("corpus.only").orNull?.let { systemProperty("corpus.only", it) }
}
tasks.register<Test>("corpusUpdate") {
    description = "Rewrites the expected files of the binding corpus from what the binders say; review the diff."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter.includeTestsMatching("*BindingCorpusSpec")
    systemProperty("corpus.update", "true")
    outputs.upToDateWhen { false }
}

// The corpus compiles 31 units in process (about 70 s of a 2 minute job). Its verdicts do not depend on the graphql-java leg
// (the floor and ceiling runs record the same verdicts as the default leg), so it runs on the default leg only, on JDK 21 and 25;
// the floor and ceiling legs run every other test of this module unchanged. CorpusRunnerSpec (the runner's own tests, 3 s) runs on every leg.
val corpusOnThisLeg = providers.gradleProperty("graphqlJavaLeg").orElse("default").get() == "default"
tasks.named<Test>("test") {
    exclude("**/KotlinCorpusSpec*")
    if (!corpusOnThisLeg && !project.hasProperty("corpus.update")) {
        exclude("**/BindingCorpusSpec*")
        exclude("**/ErrorReferenceSpec*")
        doFirst {
            logger.lifecycle("BindingCorpusSpec skipped: the binding corpus runs on the default graphql-java leg only (-PgraphqlJavaLeg=default).")
        }
    }
}
// The Kotlin corpus (#143): one batched Gradle TestKit build with the production toolchain, run on the checked and the unchecked
// variant of every case. Not part of `check` or `build`: it needs the network and builds real projects, so the CI job `build tools`
// runs it (-Pcorpus.kotlin.processor=kapt runs the same cases under kapt, in the canary workflow).
fun pluginPin(id: String): String =
    Regex("""id\("${Regex.escape(id)}"\) version "([^"]+)"""")
        .find(rootProject.file("build.gradle.kts").readText())?.groupValues?.get(1)
        ?: throw GradleException("The root build.gradle.kts has no version for the plugin $id")
tasks.register<Test>("corpusKotlin") {
    description = "Compiles the Kotlin cases of the binding corpus in one TestKit build (Kotlin, KSP2 or kapt) and judges them."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter.includeTestsMatching("*KotlinCorpusSpec")
    systemProperty("corpus.gradle-user-home", gradle.gradleUserHomeDir.absolutePath)
    systemProperty("corpus.micronaut.platform", libs.versions.micronaut.platform.get())
    systemProperty("corpus.kotlin.version", pluginPin("org.jetbrains.kotlin.jvm"))
    systemProperty("corpus.ksp.version", pluginPin("com.google.devtools.ksp"))
    systemProperty("corpus.kotlin.dir", layout.buildDirectory.dir("corpus-kotlin").get().asFile.absolutePath)
    providers.gradleProperty("corpus.kotlin.processor").orNull?.let { systemProperty("corpus.kotlin.processor", it) }
    outputs.upToDateWhen { false }
}

// ProcessorOptionsDocsSpec reads the options page next to this module behind Gradle's back, so it is a declared input.
tasks.named<Test>("test") {
    val repositoryRoot = rootProject.layout.projectDirectory
    systemProperty("docs.root", repositoryRoot.asFile.absolutePath)
    inputs.files(repositoryRoot.asFileTree.matching { include("src/main/docs/guide/compileTime/**") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("docsOptionsPage")
}

// RegistrationSpec opens the jar of this module: it holds the service file of the visitor and no other registration (#291).
tasks.named<Test>("test") {
    val jar = tasks.named<Jar>("jar")
    inputs.files(jar).withPropertyName("processorJar")
    systemProperty("processor.jar", jar.get().archiveFile.get().asFile.absolutePath)
}

// VisitorArchitectureSpec reads the sources of the visitor behind Gradle's back.
tasks.named<Test>("test") {
    val visitorSources = layout.projectDirectory.dir("src/main/java/io/micronaut/graphql/tools/visitor")
    systemProperty("visitor.sources", visitorSources.asFile.absolutePath)
    inputs.files(visitorSources.asFileTree).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("visitorSources")
}

// The processor runs inside a compiler and must never put the runtime on its path: the main classpaths hold no
// `graphql-tools` project and no `micronaut-graphql-tools` artifact. The test classpath may, for the dual-binder corpus.
val checkNoRuntimeDependency by tasks.registering {
    description = "Fails when a main classpath of this module contains the graphql-tools runtime."
    group = "verification"
    val components = listOf("compileClasspath", "runtimeClasspath").map { name ->
        configurations.named(name).map { configuration ->
            configuration.incoming.resolutionResult.allComponents.map { it.id.displayName }
        }
    }
    inputs.property("components", providers.provider { components.flatMap { it.get() } })
    doLast {
        val runtime = components.flatMap { it.get() }.filter {
            it == "project :graphql-tools" || it.startsWith("io.micronaut.graphql.tools:micronaut-graphql-tools:")
        }
        check(runtime.isEmpty()) { "graphql-tools-processor must not depend on the runtime, found: $runtime" }
    }
}
tasks.named("check") {
    dependsOn(checkNoRuntimeDependency)
}
