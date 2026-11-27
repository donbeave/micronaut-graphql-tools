plugins {
    id("io.micronaut.build.internal.graphql-tools-plain-module")
}

// The Maven plugin with the generate goal (#195): a plain Java module that maps a <configuration> onto the options of the code
// generator and wires the generated sources into the project. The Maven API is compile-only: the running Maven provides it, and the
// published POM names only the generator. The plugin descriptor is written by hand (src/main/resources/META-INF/maven/plugin.xml)
// and its version is filtered in; the parity of its parameters with the options is asserted by PluginDescriptorSpec.
dependencies {
    implementation(projects.micronautGraphqlToolsCodegen)
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.plugin.annotations)

    testImplementation(mnTest.micronaut.test.spock)
    testImplementation("org.spockframework:spock-junit4")
    testImplementation(libs.maven.plugin.api)
    testImplementation(libs.maven.core)
    testImplementation(libs.maven.plugin.testing.harness)
    testRuntimeOnly(libs.maven.compat)
    testRuntimeOnly(mnLogging.logback.classic)
}

tasks.processResources {
    inputs.property("version", project.version.toString())
    filesMatching("META-INF/maven/plugin.xml") {
        filter(org.apache.tools.ant.filters.ReplaceTokens::class, "tokens" to mapOf("version" to project.version.toString()))
    }
}

tasks.named<Test>("test") {
    systemProperty("plugin.version", project.version.toString())
    systemProperty("plugin.sources", layout.projectDirectory.dir("src/main").asFile.absolutePath)
    inputs.files(layout.projectDirectory.dir("src/main").asFileTree)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("pluginSources")
}

// The Micronaut platform manages Guice 7 (the jakarta.inject line), but Maven 3.9 runs on Sisu 0.9, which needs Guice 5.1 (javax.inject).
// The harness starts the Maven container of the test class path, so the tests keep the Guice that maven-core declares. Tests only.
configurations.matching { it.name.startsWith("test") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.google.inject") {
            useVersion("5.1.0")
            because("Sisu of Maven 3.9 does not run on the Guice 7 that the Micronaut platform manages")
        }
    }
}
