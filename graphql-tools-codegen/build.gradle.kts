import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier

plugins {
    id("io.micronaut.build.internal.graphql-tools-plain-module")
}

// The code generator (#193): a plain Java library that the Gradle and Maven plugins call. It runs on the build's class path, never
// on an application's, and compiles with --release 17 like the other modules, because the plugins run on JDK 17. graphql-java is
// the parser only. The sourcegen generator is declared non-transitive: only its JavaPoet fork is used, and the fork runs with the
// JDK alone. The Micronaut platform manages the versions of the sourcegen generator and KotlinPoet.
dependencies {
    implementation(platform(libs.micronaut.platform))
    api(mnGraphql.graphql.java)
    implementation("io.micronaut.sourcegen:micronaut-sourcegen-generator-java") {
        isTransitive = false
    }
    implementation("com.squareup:kotlinpoet")

    testImplementation(mnTest.micronaut.test.spock)
    testRuntimeOnly(mnLogging.logback.classic)
}

// The generator is a build-time library: no Gradle API, no Maven API and no Micronaut runtime or injection on a main classpath, and
// no other module of this repository. The BOMs that manage the versions are not dependencies. The check reads the resolved components by their identifier, as checkNoGraphQLJava of the
// client does.
val checkNoBuildToolOrRuntimeDependency = tasks.register("checkNoBuildToolOrRuntimeDependency") {
    description = "Fails when a main classpath of this module contains a Gradle, Maven or Micronaut runtime dependency."
    group = "verification"
    val components = listOf("compileClasspath", "runtimeClasspath").map { name ->
        configurations.named(name).map { configuration ->
            configuration.incoming.resolutionResult.allComponents.map { component ->
                when (val id = component.id) {
                    is ProjectComponentIdentifier -> "project ${id.projectPath}"
                    is ModuleComponentIdentifier -> "${id.group}:${id.module}"
                    else -> id.displayName
                }
            }
        }
    }
    inputs.property("components", providers.provider { components.flatMap { it.get() } })
    doLast {
        val self = "project ${project.path}"
        val forbidden = components.flatMap { it.get() }.filter { !it.endsWith("-bom") && !it.endsWith(":micronaut-platform") && it != self }.filter {
            it.startsWith("project ") || it.startsWith("org.gradle") || it.startsWith("dev.gradleplugins:") ||
                it.startsWith("org.apache.maven") || it.startsWith("io.micronaut:") || it.startsWith("io.micronaut.graphql.tools:")
        }
        check(forbidden.isEmpty()) {
            "graphql-tools-codegen must not depend on Gradle, Maven, Micronaut runtime or another module of this repository, found: $forbidden"
        }
    }
}
tasks.named("check") {
    dependsOn(checkNoBuildToolOrRuntimeDependency)
}

tasks.named<Test>("test") {
    // OptionsDocumentationSpec reads the Javadoc of Options behind Gradle's back.
    systemProperty("codegen.sources", layout.projectDirectory.dir("src/main/java/io/micronaut/graphql/tools/codegen").asFile.absolutePath)
    // IrGoldenSpec compares the IR snapshots in src/test/resources/golden; -Pcodegen.golden.update=true rewrites them.
    systemProperty("codegen.golden", layout.projectDirectory.dir("src/test/resources/golden").asFile.absolutePath)
    providers.gradleProperty("codegen.golden.update").orNull?.let { systemProperty("codegen.golden.update", it) }
    inputs.files(layout.projectDirectory.dir("src/main/java").asFileTree)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("codegenSources")
}
