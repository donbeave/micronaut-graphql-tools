plugins {
    id("io.micronaut.build.internal.parent")
}

// graphql-java legs by role (D-35). The library is built and tested against one leg at a time:
//   ./gradlew build                              the default leg: graphql-java as micronaut-graphql manages it, nothing pinned
//   ./gradlew build -PgraphqlJavaLeg=floor       the oldest supported line
//   ./gradlew build -PgraphqlJavaLeg=ceiling     the newest tested line
//   ./gradlew build -PgraphqlJavaLeg=latest      the newest release of every graphql-java library (the canary only)
// Binary compatibility: main code compiled on one leg, tested on another:
//   ./gradlew build -PgraphqlJavaLeg=ceiling -PgraphqlJavaCompileLeg=default
// The versions live in gradle/libs.versions.toml. A non-default leg pins its versions for dependency resolution inside this
// build. The publications take their versions from the resolved classpath, so a non-default leg would put its pins into the
// published POM and module metadata: publishing is only allowed from the default leg (guard below).
if (providers.gradleProperty("graphqlJavaVersion").isPresent) {
    throw GradleException(
        "-PgraphqlJavaVersion is no longer supported. Select a graphql-java leg by role with " +
                "-PgraphqlJavaLeg=floor|default|ceiling|latest; the versions are in gradle/libs.versions.toml."
    )
}

// graphql-java and the libraries whose versions must move with it, by Maven coordinates ("group:name")
val legPins: Map<String, Map<String, String>> = mapOf(
    "floor" to mapOf(
        "com.graphql-java:graphql-java" to libs.versions.floor.graphql.java.get(),
        "com.graphql-java:graphql-java-extended-scalars" to libs.versions.floor.extended.scalars.get(),
        "com.graphql-java:java-dataloader" to libs.versions.floor.java.dataloader.get(),
        // test scope only: the directive wiring tests run graphql-java-extended-validation
        "com.graphql-java:graphql-java-extended-validation" to libs.versions.floor.extended.validation.get(),
    ),
    // nothing is pinned: graphql-java comes through micronaut-graphql
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

// the leg the tests run on, and the leg main code is compiled on (the same, unless the binary compatibility job says so)
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
        // main code is compiled on the compile leg; everything that runs, and the tests, use the leg under test
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
    // runtime. Plain deprecations stay warnings: some replacements do not exist on the oldest leg.
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:removal", "-Werror"))
    }
}
