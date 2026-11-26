plugins {
    id("io.micronaut.build.internal.graphql-tools-compatibility")
    id("io.micronaut.build.internal.graphql-tools-api-snapshot")
}

// The configuration that every published module shares. A convention plugin applies a Micronaut module plugin first, then this one:
// io.micronaut.build.internal.graphql-tools-module for a module that uses Micronaut injection, and
// io.micronaut.build.internal.graphql-tools-plain-module for one that does not.

// The quality plugin checks the headers of src/main only; the license plugin checks the test sources.
extra["licenseSourceSets"] = "test"
pluginManager.apply("io.micronaut.build.internal.graphql-tools-license")

// micronaut-build 7.6.6 sets only sourceCompatibility and targetCompatibility from micronautBuild.javaVersion
// (default 17). Set `release` too, so the shipped modules cannot use APIs newer than that Java version.
tasks.withType<JavaCompile>().configureEach {
    val micronautBuild = project.extensions.getByType<io.micronaut.build.MicronautBuildExtension>()
    options.release = provider { micronautBuild.javaVersion.get().toInt() }
}

val libs = versionCatalogs.named("libs")

// logback and JUnit are managed by the Micronaut platform BOM, which the core BOM behind `mn` is not: tests only, so that
// nothing published changes.
dependencies {
    "testImplementation"(platform(libs.findLibrary("micronaut-platform").get()))
}

// Gradle 9 does not put junit-platform-launcher on the test runtime classpath by itself.
dependencies {
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

// The Micronaut BOMs manage Groovy 4.0.28, which cannot read JDK 27 class files; 4.0.33 is the newest 4.0.x.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.groovy") {
            useVersion(libs.findVersion("groovy").get().requiredVersion)
            because("Groovy 4.0.33 reads JDK 27 class files; the BOM's 4.0.28 does not")
        }
    }
}
