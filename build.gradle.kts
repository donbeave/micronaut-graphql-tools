plugins {
    id("io.micronaut.build.internal.parent")

    id("io.micronaut.application") version "4.6.2" apply false
    id("com.gradleup.shadow") version "8.3.11" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.21" apply false
    id("com.google.devtools.ksp") version "2.1.21-2.0.2" apply false
    id("io.micronaut.aot") version "4.6.2" apply false
    id("com.apollographql.apollo") version "5.2.0" apply false
}

if (providers.gradleProperty("graphqlJavaVersion").isPresent) {
    throw GradleException(
        "-PgraphqlJavaVersion is no longer supported. Select a graphql-java leg by role with " +
                "-PgraphqlJavaLeg=floor|default|ceiling|latest; the versions are in gradle/libs.versions.toml."
    )
}

val legPins: Map<String, Map<String, String>> = mapOf(
    "floor" to mapOf(
        "com.graphql-java:graphql-java" to libs.versions.floor.graphql.java.get(),
        "com.graphql-java:graphql-java-extended-scalars" to libs.versions.floor.extended.scalars.get(),
        "com.graphql-java:java-dataloader" to libs.versions.floor.java.dataloader.get(),
        "com.graphql-java:graphql-java-extended-validation" to libs.versions.floor.extended.validation.get(),
    ),
    "default" to emptyMap(),
    "ceiling" to mapOf(
        "com.graphql-java:graphql-java" to libs.versions.ceiling.graphql.java.get(),
        "com.graphql-java:graphql-java-extended-scalars" to libs.versions.ceiling.extended.scalars.get(),
        "com.graphql-java:java-dataloader" to libs.versions.ceiling.java.dataloader.get(),
        "com.graphql-java:graphql-java-extended-validation" to libs.versions.ceiling.extended.validation.get(),
    ),
    // The newest release of each library. `latest.release` is not used: these libraries have published date-named versions
    // ("230521-nf-execution", "2023-01-24T02-11-56-babda5f") that Gradle sorts above every real version, so the range
    // starts at the first real release line and ends above any real major.
    "latest" to listOf(
        "graphql-java", "graphql-java-extended-scalars", "java-dataloader", "graphql-java-extended-validation",
    ).associate { "com.graphql-java:$it" to "[1.0,1000)" },
)

fun selectedLeg(property: String, fallback: String): String {
    val leg = providers.gradleProperty(property).getOrElse(fallback)
    if (leg !in legPins) {
        throw GradleException("Unsupported -P$property=$leg. Supported legs: ${legPins.keys.joinToString()}")
    }
    return leg
}

val graphqlJavaLeg = selectedLeg("graphqlJavaLeg", "default")
val graphqlJavaCompileLeg = selectedLeg("graphqlJavaCompileLeg", graphqlJavaLeg)
logger.lifecycle("Building against graphql-java leg '$graphqlJavaLeg' (compiled on '$graphqlJavaCompileLeg'): " +
        (legPins.getValue(graphqlJavaLeg).takeIf { it.isNotEmpty() }
            ?.entries?.joinToString { "${it.key.substringAfter(':')} ${it.value}" }
            ?: "graphql-java as managed by micronaut-graphql, nothing pinned"))

if (graphqlJavaLeg != "default" || graphqlJavaCompileLeg != "default") {
    gradle.taskGraph.whenReady {
        val publishTasks = allTasks.filter { it.name.startsWith("publish") }
        if (publishTasks.isNotEmpty()) {
            throw GradleException(
                "Publishing is only allowed from the default graphql-java leg: this build uses " +
                        "-PgraphqlJavaLeg=$graphqlJavaLeg -PgraphqlJavaCompileLeg=$graphqlJavaCompileLeg, whose pins would " +
                        "end up in the published POM. Tasks: ${publishTasks.joinToString { it.path }}"
            )
        }
    }
}

subprojects {
    if (name.endsWith("-bom")) {
        return@subprojects
    }

    configurations.configureEach {
        val compileSide = name == "compileClasspath" || name.endsWith("AnnotationProcessor") || name == "annotationProcessor"
        val leg = if (compileSide) graphqlJavaCompileLeg else graphqlJavaLeg
        val pins = legPins.getValue(leg)
        if (pins.isNotEmpty()) {
            resolutionStrategy.eachDependency {
                val pinned = pins["${requested.group}:${requested.name}"]
                if (pinned != null) {
                    useVersion(pinned)
                    because("graphql-java leg $leg")
                }
            }
        }
    }

    // An API that graphql-java removes on a newer leg must fail compilation, not surface as NoSuchMethodError at
    // runtime. Plain deprecations stay warnings: some replacements do not exist on the oldest leg. -Werror is set
    // only on the library's compileJava (graphql-tools/build.gradle.kts), so warnings elsewhere do not fail the build.
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:removal")
    }

    // -PtestJdk=17 runs the tests on that JDK while Gradle itself runs on a newer one. The library check on JDK 17 needs it: the test
    // compilation of graphql-tools runs the visitor of graphql-tools-processor, which is Java 21 bytecode (#144), so Gradle runs on 21.
    providers.gradleProperty("testJdk").orNull?.let { testJdk ->
        tasks.withType<Test>().configureEach {
            javaLauncher.set(project.extensions.getByType<JavaToolchainService>().launcherFor {
                languageVersion.set(JavaLanguageVersion.of(testJdk.toInt()))
            })
        }
    }
}

// The guide takes its code examples from the test-suite-* sources with the `snippet::` macro. The macro reads them behind
// Gradle's back, so without these inputs a changed or removed example would leave publishGuide up to date.
tasks.named("publishGuide") {
    inputs.files(fileTree(".") { include("test-suite-*/src/main/**", "test-suite-*/src/test/**") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("snippetSources")
}
