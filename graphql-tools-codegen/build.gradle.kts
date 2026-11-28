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
    // ScalarPresetsSpec compares the presets with the runtime catalog and the standard scalars.
    testImplementation(projects.micronautGraphqlToolsBinding)
    testImplementation(projects.micronautGraphqlToolsScalars)
    // ModelRecordSpec compiles the generated records, which use @GraphQLType and the JSpecify annotations.
    testImplementation(projects.micronautGraphqlTools)
    testImplementation("org.jspecify:jspecify")
    // The client emitter specs (#671) compile the generated client sources with the compile-time checks of the client (the visitor of the
    // processor module, the Micronaut inject and Serde processors) and decode responses with Micronaut Serialization.
    testImplementation(projects.micronautGraphqlToolsClient)
    testImplementation(projects.micronautGraphqlToolsProcessor)
    testImplementation(mn.micronaut.inject.java)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mnSerde.micronaut.serde.processor)
    testRuntimeOnly(mnLogging.logback.classic)
}

// What the generated client sources compile against, as the guide documents it: the client module and the JSON implementation of
// Micronaut Serialization, with whatever they bring (JSpecify, the Jackson annotations and the Micronaut APIs), and nothing else.
val generatedClientClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    }
}
// The same, as an application runs it: the class path that the decoding tests start Micronaut Serialization on.
val generatedClientRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    }
}
// The Kotlin compiler that compiles the Kotlin callers of the generated Java (CodegenKotlinCallerSpec). Its version is the Kotlin of the
// test suites, which the Micronaut BOM does not manage (it manages 1.9.x); the compiler runs in a process of its own with this class path.
val kotlinCompiler by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    generatedClientClasspath(platform(libs.micronaut.platform))
    generatedClientClasspath(projects.micronautGraphqlToolsClient)
    generatedClientClasspath(mnSerde.micronaut.serde.jackson)
    generatedClientRuntimeClasspath(platform(libs.micronaut.platform))
    generatedClientRuntimeClasspath(projects.micronautGraphqlToolsClient)
    generatedClientRuntimeClasspath(mnSerde.micronaut.serde.jackson)
    generatedClientRuntimeClasspath(mnLogging.logback.classic)
    kotlinCompiler(libs.kotlin.compiler.embeddable)
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
    inputs.files(generatedClientClasspath).withPropertyName("generatedClientClasspath")
    inputs.files(generatedClientRuntimeClasspath).withPropertyName("generatedClientRuntimeClasspath")
    inputs.files(kotlinCompiler).withPropertyName("kotlinCompiler")
    // resolved when the task runs, so that the jar of the client module is built first
    doFirst {
        systemProperty("codegen.client.classpath", generatedClientClasspath.asPath)
        systemProperty("codegen.client.runtime.classpath", generatedClientRuntimeClasspath.asPath)
        systemProperty("codegen.kotlin.compiler", kotlinCompiler.asPath)
    }
    // OptionsDocumentationSpec reads the Javadoc of Options behind Gradle's back.
    systemProperty("codegen.sources", layout.projectDirectory.dir("src/main/java/io/micronaut/graphql/tools/codegen").asFile.absolutePath)
    // IrGoldenSpec compares the IR snapshots in src/test/resources/golden; -Pcodegen.golden.update=true rewrites them.
    systemProperty("codegen.golden", layout.projectDirectory.dir("src/test/resources/golden").asFile.absolutePath)
    providers.gradleProperty("codegen.golden.update").orNull?.let { systemProperty("codegen.golden.update", it) }
    systemProperty("codegen.readme", layout.projectDirectory.file("README.md").asFile.absolutePath)
    inputs.file(layout.projectDirectory.file("README.md")).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("codegenReadme")
    inputs.files(layout.projectDirectory.dir("src/main/java").asFileTree)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("codegenSources")
}

// The golden harness (#192) reads the snapshots from the source tree and rewrites them in place with -PupdateGolden.
val updateGolden = providers.gradleProperty("updateGolden").isPresent
tasks.named<Test>("test") {
    systemProperty("codegen.golden.dir", layout.projectDirectory.dir("src/test/resources/golden").asFile.absolutePath)
    systemProperty("codegen.golden.update", updateGolden.toString())
    if (updateGolden) {
        outputs.upToDateWhen { false }
    }
}
