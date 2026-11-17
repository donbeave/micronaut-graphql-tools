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
    testRuntimeOnly(libs.logback.classic)
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

tasks.named<Test>("test") {
    exclude("**/KotlinCorpusSpec*")
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
