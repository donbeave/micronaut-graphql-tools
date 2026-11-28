plugins {
    id("io.micronaut.build.internal.parent")

    id("io.micronaut.application") version "4.6.2" apply false
    id("com.gradleup.shadow") version "8.3.11" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.21" apply false
    id("com.google.devtools.ksp") version "2.1.21-2.0.2" apply false
    id("io.micronaut.aot") version "4.6.2" apply false
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

    pluginManager.apply("io.micronaut.build.internal.graphql-tools-test-timeout")

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
}

// The guide takes its code examples from the docs-examples-* and test-suite-* sources with the `snippet::` macro. The macro reads them behind
// Gradle's back, so without these inputs a changed or removed example would leave publishGuide up to date.
tasks.named("publishGuide") {
    inputs.files(fileTree(".") { include("test-suite-*/src/main/**", "test-suite-*/src/test/**", "test-suite-*/src/driftFixture/**", "test-suite-*/src/serdeTest/**",
        "docs-examples-*/src/main/**", "docs-examples-*/src/test/**") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("snippetSources")
}

// The `api:` macro of the guide links a type to its Javadoc page and does not fail on a type that is gone, so a renamed or removed
// type would leave a dead link. This check fails the guide build on an `api:` link whose type has no source file in a module.
val checkApiLinks by tasks.registering {
    group = "verification"
    description = "Checks that every api: link of the guide names a top-level type that exists in a library module."
    val guide = fileTree(layout.projectDirectory.dir("src/main/docs/guide")) { include("**/*.adoc") }
    val sources = fileTree(layout.projectDirectory) { include("graphql-tools*/src/main/java/**/*.java") }
    inputs.files(guide).withPropertyName("guide").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(sources).withPropertyName("sources").withPathSensitivity(PathSensitivity.RELATIVE)
    val marker = layout.buildDirectory.file("checks/api-links.ok")
    outputs.file(marker)
    doLast {
        val known = sources.files.map { it.invariantSeparatorsPath.substringAfter("/src/main/java/").removeSuffix(".java").replace('/', '.') }.toSet()
        val link = Regex("""api:([\w.]+)\[""")
        val missing = guide.files.sortedBy { it.name }.flatMap { page ->
            page.readLines().flatMap { line -> link.findAll(line).map { it.groupValues[1] }.toList() }
                .filter { type -> type !in known }
                .map { "${page.name}: api:$it[]" }
        }
        if (missing.isNotEmpty()) {
            throw GradleException("The guide has api: links to types that do not exist:\n" + missing.joinToString("\n"))
        }
        marker.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}
tasks.named("publishGuide") { dependsOn(checkApiLinks) }

// The vulnerability audit (.github/scripts/vulnerability-audit.sh) scans this file with osv-scanner. It lists what the published
// modules resolve at runtime on the default graphql-java leg, in the format of a Gradle dependency lockfile.
// The published library modules, derived from the folders so that a new module is audited without a second list.
fun isAuditModule(project: Project) = project.projectDir.name.startsWith("graphql-tools") && !project.projectDir.name.endsWith("-bom")

subprojects.filter { isAuditModule(it) }.forEach { module ->
    with(module) {
        val coordinates = objects.setProperty<String>()
        coordinates.addAll(configurations.named("runtimeClasspath").flatMap { it.incoming.artifacts.resolvedArtifacts }.map { artifacts ->
            artifacts.map { it.id.componentIdentifier }
                .filterIsInstance<org.gradle.api.artifacts.component.ModuleComponentIdentifier>()
                .map { "${it.group}:${it.module}:${it.version}=runtimeClasspath" }
        })
        tasks.register("auditCoordinates") {
            inputs.property("coordinates", coordinates)
            val output = layout.buildDirectory.file("audit/coordinates.txt")
            outputs.file(output)
            doLast { output.get().asFile.apply { parentFile.mkdirs() }.writeText(coordinates.get().sorted().joinToString("\n", postfix = "\n")) }
        }
    }
}

val auditLockfile by tasks.registering {
    group = "verification"
    description = "Writes the runtime dependencies of the published modules as a gradle.lockfile for the vulnerability audit."
    val parts = subprojects.filter { isAuditModule(it) }.map { it.layout.buildDirectory.file("audit/coordinates.txt") }
    dependsOn(subprojects.filter { isAuditModule(it) }.map { "${it.path}:auditCoordinates" })
    inputs.files(parts).withPropertyName("coordinates")
    val output = layout.buildDirectory.file("audit/gradle.lockfile")
    outputs.file(output)
    doLast {
        val lines = parts.flatMap { it.get().asFile.readLines() }.filter { it.isNotBlank() }.toSortedSet()
        output.get().asFile.apply { parentFile.mkdirs() }.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}

// The native coverage table of CONTRIBUTING.md names a test class per feature category and suite, and the TCK scenarios it
// relies on. The native runs execute the whole `test` source set of each suite and the `serdeTest` source set of the Java suite, so a
// row whose class is missing from them means a category lost its test.
val checkNativeCoverage by tasks.registering {
    group = "verification"
    description = "Checks that every row of the native coverage table of CONTRIBUTING.md names existing tests and scenarios."
    val contributing = layout.projectDirectory.file("CONTRIBUTING.md")
    val scenarios = layout.projectDirectory.file("test-suite-tck/src/main/resources/tck/scenarios.txt")
    val sources = fileTree(layout.projectDirectory) {
        include("test-suite-java/src/test/**/*.java", "test-suite-java/src/serdeTest/**/*.java", "test-suite-kotlin/src/test/**/*.kt")
    }
    inputs.files(contributing, scenarios, sources).withPathSensitivity(PathSensitivity.RELATIVE)
    val marker = layout.buildDirectory.file("checks/native-coverage.ok")
    outputs.file(marker)
    doLast {
        val text = contributing.asFile.readText()
        val table = text.substringAfter("<!-- native-coverage:begin -->").substringBefore("<!-- native-coverage:end -->")
        val rows = table.lines().filter { it.startsWith("| ") && !it.startsWith("| Category") && !it.startsWith("| ---") }
        if (rows.isEmpty()) throw GradleException("CONTRIBUTING.md has no native coverage table")
        val known = scenarios.asFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        val problems = mutableListOf<String>()
        rows.forEach { row ->
            val cells = row.trim().trim('|').split("|").map { it.trim() }
            val category = cells[0]
            val testClass = cells[1].trim('`')
            val ids = cells[2].split(",").map { it.trim().trim('`') }.filter { it.isNotEmpty() }
            val suites = cells[3].split(",").map { it.trim() }
            val path = testClass.replace('.', '/')
            suites.forEach { suite ->
                val (module, extension) = when (suite) {
                    "Java" -> "test-suite-java" to "java"
                    "Kotlin" -> "test-suite-kotlin" to "kotlin"
                    else -> throw GradleException("Unknown suite '$suite' in the native coverage row '$category'")
                }
                val name = "$path.${if (extension == "kotlin") "kt" else extension}"
                val roots = if (module == "test-suite-java") listOf("test", "serdeTest") else listOf("test")
                if (roots.none { layout.projectDirectory.file("$module/src/$it/$extension/$name").asFile.isFile }) {
                    problems += "$category: $testClass is missing from $module"
                }
            }
            ids.filter { it !in known }.forEach { problems += "$category: scenario '$it' is not in scenarios.txt" }
        }
        if (problems.isNotEmpty()) throw GradleException("Native coverage table is out of date:\n" + problems.joinToString("\n"))
        marker.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}
tasks.matching { it.name == "check" }.configureEach { dependsOn(checkNativeCoverage) }

// The tests of the build logic in buildSrc (the public API snapshot tasks, #784). Gradle does not run them with the build, so they
// run as a build of their own, on a copy of buildSrc: the build in progress already uses buildSrc/build, and a second build writing
// the same directory made the tests fail on CI with classes missing from their classpath. Only when something under buildSrc or the
// version catalog changed: the marker file keeps the task up to date otherwise, at no cost.
val buildSrcTestCopy = layout.buildDirectory.dir("buildsrc-test")
val prepareBuildSrcTest by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("buildSrc")) {
        include("src/**", "*.kts")
        into("buildSrc")
    }
    from(layout.projectDirectory.file("gradle/libs.versions.toml")) { into("gradle") }
    into(buildSrcTestCopy)
}
val buildSrcTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the tests of the build logic in buildSrc, on a copy of it."
    dependsOn(prepareBuildSrcTest)
    val copy = buildSrcTestCopy.map { it.dir("buildSrc").asFile }
    val marker = layout.buildDirectory.file("checks/buildsrc-test.ok")
    outputs.file(marker)
    inputs.files(
        fileTree(layout.projectDirectory.dir("buildSrc")) { include("src/**", "*.kts") },
        layout.projectDirectory.file("gradle/libs.versions.toml"),
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("buildLogic")
    workingDir = layout.projectDirectory.asFile
    argumentProviders.add(CommandLineArgumentProvider { listOf("--project-dir", copy.get().absolutePath, "test", "--console=plain") })
    executable(layout.projectDirectory.file("gradlew").asFile.absolutePath)
    doLast { marker.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n") }
}
tasks.matching { it.name == "check" }.configureEach { dependsOn(buildSrcTest) }

// The annotation processors of the test suites run inside the compile tasks, and Micronaut copies the options of a compilation into the
// system properties of the JVM and lets those override the options of the next compilation (JavaVisitorContext#getOptions merges the
// system properties last). Two compile tasks that run at once in one Gradle daemon therefore read each other's schema.dirs, client.dirs
// and module.dir, and a client visitor reports the module of the other task as the owner of its directories (client-schema-missing in
// test-suite-serde:compileTestJava while a test suite of another module compiled). The compile tasks of the modules that apply the
// processors take one lock, so they run one at a time.
abstract class ProcessorCompileLock : BuildService<BuildServiceParameters.None>

val processorCompileLock = gradle.sharedServices.registerIfAbsent("processorCompileLock", ProcessorCompileLock::class.java) {
    maxParallelUsages.set(1)
}
subprojects.filter { it.path.startsWith(":test-suite-") }.forEach { suite ->
    suite.tasks.matching {
        it is JavaCompile || it is GroovyCompile || it.name.startsWith("ksp") || it.name.startsWith("kapt")
    }.configureEach { usesService(processorCompileLock) }
}
