plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

dependencies {
    implementation(mnGraphql.graphql.java)

    testImplementation(projects.micronautGraphqlTools)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.inject.java)
    // the corpus compiles Groovy cases in process, and Micronaut's AST transformation writes their bean definitions
    testImplementation(mn.micronaut.inject.groovy)
    // the Kotlin side of the corpus compiles its cases in a batched TestKit build
    testImplementation(gradleTestKit())
    testRuntimeOnly("ch.qos.logback:logback-classic")
}

micronautBuild {
    // Java 21 until #144 deletes the JSR-269 `processing/` package: it uses Elements.getFileObjectOf (JDK 18+) and no
    // JDK 17 fallback is written for code that is going away. #144 moves this module to the default (release 17).
    javaVersion = 21
    binaryCompatibility {
        enabled.set(false)
    }
}

// The binding corpus (#142): the cases are data under src/test/resources/corpus, read from the project directory.
// `-Pcorpus.update` (task corpusUpdate) rewrites the expected files instead of comparing; the diff is the review.
val corpusDir = layout.projectDirectory.dir("src/test/resources/corpus")
tasks.withType<Test>().configureEach {
    inputs.files(corpusDir).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("corpus")
    // the Kotlin batch is its own task (corpusKotlin): it needs the network and has its own budget
    systemProperty("corpus.dir", corpusDir.asFile.absolutePath)
    systemProperty("corpus.report", layout.buildDirectory.file("corpus/timings.txt").get().asFile.absolutePath)
    if (project.hasProperty("corpus.update")) {
        systemProperty("corpus.update", "true")
    }
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
        doFirst {
            logger.lifecycle("BindingCorpusSpec skipped: the binding corpus runs on the default graphql-java leg only (-PgraphqlJavaLeg=default).")
        }
    }
}
// The Kotlin corpus (DD-13): one batched Gradle TestKit build with the production toolchain, budget 3 minutes on JDK 25.
// Not part of `check` until the CI job for it exists (CT-WIRING, #143).
tasks.register<Test>("corpusKotlin") {
    description = "Compiles the Kotlin cases of the binding corpus in one TestKit build (Kotlin, KSP2) and judges them."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter.includeTestsMatching("*KotlinCorpusSpec")
    systemProperty("corpus.gradle-user-home", gradle.gradleUserHomeDir.absolutePath)
    systemProperty("corpus.micronaut.platform", libs.versions.micronaut.platform.get())
    systemProperty("corpus.kotlin.dir", layout.buildDirectory.dir("corpus-kotlin").get().asFile.absolutePath)
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

// The processor runs inside a compiler and must never put the runtime on its path (D-39): the main classpaths hold no
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
