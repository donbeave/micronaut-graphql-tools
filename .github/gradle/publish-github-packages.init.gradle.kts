// Publishes the consumer modules to the GitHub Packages Maven registry of this repository (milestone and release candidate previews).
//
// The release workflow passes this file with `-I`, so the tagged source tree is built unchanged: the repository, the POM
// metadata and the safety checks are added to the publications at publish time, and nothing in the build files has to exist
// in the tagged commit. Nothing here runs unless the file is passed on the command line.
//
//   ./gradlew publishAllPublicationsToGitHubPackagesRepository -PprojectVersion=<version> -I .github/gradle/publish-github-packages.init.gradle.kts
//
// Credentials come from the environment (GITHUB_ACTOR and GITHUB_TOKEN); the token needs `packages: write` for this repository.
//
// This file is temporary: it is kept through the 1.0 line and deleted at the Foundation contribution (#217).

val registryUrl = "https://maven.pkg.github.com/donbeave/micronaut-graphql-tools"
val repositoryUrl = "https://github.com/donbeave/micronaut-graphql-tools"

// The only projects that are published: the library, the annotation processor, the shared binding rules module, the standard
// scalars module, the Micrometer module, the OpenTelemetry module, the upload module, the security module, the testing module,
// the client module, the code generator module, the Gradle plugin module and the BOM. Test suites, benchmarks and anything added later are never published unless they are listed here.
val publishable = setOf("micronaut-graphql-tools", "micronaut-graphql-tools-processor", "micronaut-graphql-tools-binding",
                        "micronaut-graphql-tools-scalars", "micronaut-graphql-tools-micrometer", "micronaut-graphql-tools-opentelemetry", "micronaut-graphql-tools-upload", "micronaut-graphql-tools-security", "micronaut-graphql-tools-testing", "micronaut-graphql-tools-client", "micronaut-graphql-tools-codegen", "micronaut-graphql-tools-gradle-plugin", "micronaut-graphql-tools-bom")

// A milestone (1.0.0-M<n>), a release candidate (1.0.0-RC<n>, n from 1, no leading zero) or a final release (1.0.0).
// Snapshots and any other string are refused. .github/scripts/test-release-tag.py keeps this pattern and the ones in release.yml equal.
val versionPattern = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(-(M[0-9]+|RC[1-9][0-9]*))?$")

allprojects {
    if (name !in publishable) {
        return@allprojects
    }
    plugins.withId("maven-publish") {
        afterEvaluate {
            extensions.configure<PublishingExtension> {
                repositories {
                    maven {
                        name = "GitHubPackages"
                        url = uri(registryUrl)
                        credentials {
                            username = System.getenv("GITHUB_ACTOR")
                            password = System.getenv("GITHUB_TOKEN")
                        }
                    }
                }
                publications.withType<MavenPublication>().configureEach {
                    pom {
                        // The repository is the source of truth for a pre-release preview: the module is not an official
                        // Micronaut module until the Foundation contribution.
                        description.set("${project.description ?: project.findProperty("projectDesc") ?: name.get()} " +
                                "(pre-release preview from github.com/donbeave/micronaut-graphql-tools; not an official Micronaut Foundation release)")
                        url.set(repositoryUrl)
                        scm {
                            connection.set("scm:git:https://github.com/donbeave/micronaut-graphql-tools.git")
                            developerConnection.set("scm:git:ssh://git@github.com/donbeave/micronaut-graphql-tools.git")
                            url.set(repositoryUrl)
                        }
                        issueManagement {
                            system.set("GitHub Issues")
                            url.set("$repositoryUrl/issues")
                        }
                    }
                }
            }
        }
    }
}

gradle.taskGraph.whenReady {
    val publishTasks = allTasks.filter { it.name.startsWith("publish") && !it.name.contains("MavenLocal") }
    publishTasks.forEach { task ->
        val version = task.project.version.toString()
        if (!versionPattern.matches(version)) {
            throw GradleException("Refusing to publish ${task.path}: version '$version' is not a milestone, release candidate " +
                    "or release version (expected 1.0.0-M<n>, 1.0.0-RC<n> or 1.0.0). Pass -PprojectVersion=<version>.")
        }
        if (task.project.name !in publishable) {
            throw GradleException("Refusing to publish ${task.path}: the project is not one of the published modules.")
        }
    }
}
