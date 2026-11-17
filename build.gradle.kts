plugins {
    id("io.micronaut.build.internal.parent")
}

// graphql-java compatibility legs. The library is built and tested against one leg at a time:
//   ./gradlew build                            (default leg)
//   ./gradlew build -PgraphqlJavaVersion=25.1
// Each leg pins graphql-java and the graphql-java libraries whose versions must move with it, for dependency
// resolution inside this build. The publications take their versions from the resolved classpath, so a non-default
// leg would put its pins into the published POM and module metadata: publishing is only allowed from the default leg
// (guard below).
data class GraphqlJavaLeg(
    val graphqlJava: String,
    val extendedScalars: String,
    val dataLoader: String,
    // test scope only: the directive wiring tests run graphql-java-extended-validation
    val extendedValidation: String,
)

val graphqlJavaLegs = mapOf(
    "21.3" to GraphqlJavaLeg(
        graphqlJava = "21.3",
        extendedScalars = "21.0",
        dataLoader = "3.2.1",
        extendedValidation = "21.0",
    ),
    // graphql-java-extended-scalars has no 25.x release; 24.0 links against 25.1.
    "25.1" to GraphqlJavaLeg(
        graphqlJava = "25.1",
        extendedScalars = "24.0",
        dataLoader = "6.0.0",
        // extended-validation 21.0 calls a GraphQLCodeRegistry overload that 25.x removed; 24.0 does not.
        extendedValidation = "24.0",
    ),
)

val defaultGraphqlJavaVersion = "21.3"
val graphqlJavaVersion = providers.gradleProperty("graphqlJavaVersion").getOrElse(defaultGraphqlJavaVersion)
val graphqlJavaLeg = graphqlJavaLegs[graphqlJavaVersion]
    ?: throw GradleException(
        "Unsupported graphqlJavaVersion '$graphqlJavaVersion'. Supported legs: ${graphqlJavaLegs.keys.joinToString()}"
    )
logger.lifecycle("Building against graphql-java ${graphqlJavaLeg.graphqlJava} " +
        "(extended-scalars ${graphqlJavaLeg.extendedScalars}, java-dataloader ${graphqlJavaLeg.dataLoader}, " +
        "test: extended-validation ${graphqlJavaLeg.extendedValidation})")

if (graphqlJavaVersion != defaultGraphqlJavaVersion) {
    gradle.taskGraph.whenReady {
        val publishTasks = allTasks.filter { it.name.startsWith("publish") }
        if (publishTasks.isNotEmpty()) {
            throw GradleException(
                "Publishing is only allowed from the default graphql-java leg ($defaultGraphqlJavaVersion): this build " +
                        "uses -PgraphqlJavaVersion=$graphqlJavaVersion, whose pins would end up in the published POM. " +
                        "Tasks: ${publishTasks.joinToString { it.path }}"
            )
        }
    }
}

subprojects {
    if (name.endsWith("-bom")) {
        return@subprojects
    }

    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val pinned = when {
                requested.group == "com.graphql-java" && requested.name == "graphql-java" -> graphqlJavaLeg.graphqlJava
                requested.group == "com.graphql-java" && requested.name == "graphql-java-extended-scalars" -> graphqlJavaLeg.extendedScalars
                requested.group == "com.graphql-java" && requested.name == "java-dataloader" -> graphqlJavaLeg.dataLoader
                requested.group == "com.graphql-java" && requested.name == "graphql-java-extended-validation" -> graphqlJavaLeg.extendedValidation
                else -> null
            }
            if (pinned != null) {
                useVersion(pinned)
                because("graphql-java leg $graphqlJavaVersion")
            }
        }
    }

    // An API that graphql-java removes on a newer leg must fail compilation, not surface as NoSuchMethodError at
    // runtime. Plain deprecations stay warnings: some replacements do not exist on the oldest leg.
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:removal", "-Werror"))
    }
}
