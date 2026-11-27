plugins {
    id("io.micronaut.build.internal.graphql-tools-plain-module")
    `java-gradle-plugin`
}

// The Gradle plugin of the code generator (#204): a thin adapter that maps the `graphqlTools { codegen { } }` extension onto the
// options of the generator and runs the generator in an isolated worker. The generator is compile-only here: it is resolved from
// the `graphqlToolsCodegenClasspath` configuration of the project that applies the plugin, so it is never on the build-script
// classpath or on the application's classpath. The plugin compiles with --release 17 against the Gradle API of the wrapper and
// uses only API that Gradle 8.14 has too.
dependencies {
    compileOnly(project(":micronaut-graphql-tools-codegen"))

    testImplementation(project(":micronaut-graphql-tools-codegen"))
    testImplementation(mnTest.micronaut.test.spock)
    testRuntimeOnly(mnLogging.logback.classic)
}

tasks.named<Test>("test") {
    systemProperty("plugin.version", project.version.toString())
}

val functionalTest = sourceSets.create("functionalTest")
configurations[functionalTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[functionalTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter-api")
}

gradlePlugin {
    testSourceSets(sourceSets.test.get(), functionalTest)
    plugins {
        register("graphqlTools") {
            id = "io.micronaut.graphql.tools"
            implementationClass = "io.micronaut.graphql.tools.gradle.GraphQLToolsPlugin"
            displayName = "Micronaut GraphQL Tools"
            description = "Generates Java, Kotlin or Groovy sources from GraphQL schema files and wires the compile-time checks."
        }
    }
}

// The plugin's own version is the default version of the generator: a build-time resource, so that the two cannot drift apart.
val pluginVersionDir = layout.buildDirectory.dir("generated/resources/plugin-version")
val generatePluginVersion = tasks.register("generatePluginVersion") {
    description = "Writes the version of this build to a resource that the plugin reads."
    val version = project.version.toString()
    val output = pluginVersionDir.map { it.file("io/micronaut/graphql/tools/gradle/plugin-version.properties") }
    inputs.property("version", version)
    outputs.file(output)
    doLast {
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText("version=$version\n")
        }
    }
}
sourceSets.main {
    resources.srcDir(pluginVersionDir)
}
tasks.processResources {
    dependsOn(generatePluginVersion)
}
tasks.withType<Jar>().configureEach {
    dependsOn(generatePluginVersion)
}

// java-gradle-plugin adds a `pluginMaven` publication of the same artifact as the `maven` publication of the module convention.
// Only `maven` is published; the marker publication of the plugin id stays.
tasks.withType<PublishToMavenRepository>().configureEach { onlyIf { publication.name != "pluginMaven" } }
tasks.withType<PublishToMavenLocal>().configureEach { onlyIf { publication.name != "pluginMaven" } }

// TestKit scenarios (#204): they build real projects on Gradle 8.14 and on the current 9.x, so they run in their own task that
// `check` does not run; the CI job `build tools` runs it. The generator is published to the build repository of the root project
// and the scenarios resolve it from there, as an application does.
// The Kotlin and KSP versions of the scenarios are the ones of the root build, so that a bump moves them.
fun pluginPin(id: String): String =
    Regex("""id\("${Regex.escape(id)}"\) version "([^"]+)"""")
        .find(rootProject.file("build.gradle.kts").readText())?.groupValues?.get(1)
        ?: throw GradleException("The root build.gradle.kts has no version for the plugin $id")

val functionalTestRepository = rootProject.layout.buildDirectory.dir("repo")
val lowestGradle = "8.14.3"

// Gradle 8.14 does not run on JDK 25 or newer; the scenario that uses it runs that daemon on a JDK 21 when the build runs on a newer one.
val compatJavaHome = providers.provider {
    if (JavaVersion.current() <= JavaVersion.VERSION_24) {
        System.getProperty("java.home")
    } else {
        System.getenv("JAVA_HOME_21_X64") ?: runCatching {
            javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }.get().metadata.installationPath.asFile.absolutePath
        }.getOrDefault("")
    }
}

tasks.register<Test>("functionalTest") {
    description = "Builds sample projects with Gradle TestKit on the lowest and the current Gradle."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
    dependsOn(":micronaut-graphql-tools-codegen:publishAllPublicationsToBuildRepository", "publishAllPublicationsToBuildRepository")
    inputs.dir(functionalTestRepository).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("repository")
    systemProperty("plugin.repository", functionalTestRepository.get().asFile.absolutePath)
    systemProperty("plugin.version", project.version.toString())
    systemProperty("plugin.repository-root", rootProject.projectDir.absolutePath)
    systemProperty("plugin.lowest-gradle", lowestGradle)
    systemProperty("plugin.current-gradle", gradle.gradleVersion)
    systemProperty("plugin.compat-java-home", compatJavaHome.get())
    systemProperty("plugin.gradle-user-home", gradle.gradleUserHomeDir.absolutePath)
    systemProperty("plugin.kotlin", pluginPin("org.jetbrains.kotlin.jvm"))
    systemProperty("plugin.ksp", pluginPin("com.google.devtools.ksp"))
    systemProperty("plugin.micronaut-platform", libs.versions.micronaut.platform.get())
    systemProperty("plugin.groovy", libs.versions.groovy.get())
    outputs.upToDateWhen { false }
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
