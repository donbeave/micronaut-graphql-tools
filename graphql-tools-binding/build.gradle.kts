plugins {
    id("io.micronaut.build.internal.graphql-tools-module")
}

// The binding rules: the schema rules, the per-class rules, the planner phases, the findings and their rendering. They depend
// on graphql-java and micronaut-core only and never on io.micronaut.inject (BindingArchitectureSpec), so that the runtime
// adapter and the compile-time adapter of the type model can share one body of every rule.
dependencies {
    api(mnGraphql.graphql.java)
    api(mn.micronaut.core)

    testImplementation(mnTest.micronaut.test.spock)
    testRuntimeOnly("ch.qos.logback:logback-classic")
}

// The Micronaut library convention adds micronaut-inject to every module. The rules never read the inject API, so
// the module does not publish or compile against it: a stray import fails to compile, not only BindingArchitectureSpec.
afterEvaluate {
    configurations.matching { it.name == "api" || it.name == "implementation" }.configureEach {
        dependencies.removeIf { it.group == "io.micronaut" && it.name == "micronaut-inject" }
    }
}

tasks.named<Test>("test") {
    // BindingArchitectureSpec reads the sources of this module behind Gradle's back.
    // ManifestFormatSpec reads the examples of the format specification, the manifest guide page.
    systemProperty("manifest.spec", rootProject.layout.projectDirectory.file("src/main/docs/guide/manifest.adoc").asFile.absolutePath)
    inputs.file(rootProject.layout.projectDirectory.file("src/main/docs/guide/manifest.adoc"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("manifestSpec")
    systemProperty("binding.sources", layout.projectDirectory.dir("src/main/java").asFile.absolutePath)
    inputs.files(layout.projectDirectory.dir("src/main/java").asFileTree)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("bindingSources")
}

micronautBuild {
    binaryCompatibility {
        enabled.set(false)
    }
}
