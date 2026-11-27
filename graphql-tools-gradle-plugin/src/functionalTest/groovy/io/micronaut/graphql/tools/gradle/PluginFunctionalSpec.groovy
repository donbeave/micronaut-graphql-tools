/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graphql.tools.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assumptions
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * The shared setup of the TestKit scenarios: a sample project that resolves the generator from the build repository, a runner for a
 * given Gradle version, and the rule that Gradle 8.14 needs a JDK that it supports.
 */
abstract class PluginFunctionalSpec extends Specification {

    static final String LOWEST = System.getProperty('plugin.lowest-gradle')
    static final String CURRENT = System.getProperty('plugin.current-gradle')
    static final String VERSION = System.getProperty('plugin.version')
    static final List<String> GRADLE_VERSIONS = [LOWEST, CURRENT].unique()

    static final String SCHEMA = '''type Query {
  book(id: ID!): Book
}

type Book {
  id: ID!
  title: String!
}
'''

    @TempDir
    Path project

    @TempDir
    Path buildCache

    protected void settings() {
        write('settings.gradle.kts', """
            rootProject.name = "sample"
            buildCache { local { directory = file("${buildCache.toString().replace('\\', '/')}") } }
        """.stripIndent())
        write('gradle.properties', "org.gradle.caching=true\norg.gradle.vfs.watch=false\n" + javaHome())
    }

    protected void schema(String text = SCHEMA, String file = 'src/main/resources/graphql/schema.graphqls') {
        write(file, text)
    }

    protected String kotlinBuild(String extension = 'codegen { packageName = "example" }', String plugins = 'java') {
        """
            plugins {
                $plugins
                id("io.micronaut.graphql.tools")
            }
            repositories {
                maven { url = uri("${repository()}") }
                mavenCentral()
            }
            graphqlTools {
                addRuntimeDependency = false
                $extension
            }
        """.stripIndent()
    }

    protected void write(String path, String text) {
        def file = project.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    protected BuildResult build(String gradle, String... arguments) {
        runner(gradle, arguments).build()
    }

    protected BuildResult buildAndFail(String gradle, String... arguments) {
        runner(gradle, arguments).buildAndFail()
    }

    protected GradleRunner runner(String gradle, String... arguments) {
        if (gradle == LOWEST) {
            Assumptions.assumeTrue(!javaHome().isEmpty() || Runtime.version().feature() <= 24,
                    "Gradle ${LOWEST} needs a JDK 24 or older and none is available")
        }
        GradleRunner.create()
                .withGradleVersion(gradle)
                .withProjectDir(project.toFile())
                .withTestKitDir(new File(System.getProperty('plugin.gradle-user-home')))
                .withPluginClasspath()
                .withArguments(['--stacktrace', '--warning-mode=none'] + arguments.toList())
    }

    protected boolean exists(String path) {
        Files.exists(project.resolve(path))
    }

    protected static String repository() {
        new File(System.getProperty('plugin.repository')).toURI().toString()
    }

    private static String javaHome() {
        def home = System.getProperty('plugin.compat-java-home')
        Runtime.version().feature() <= 24 || !home ? '' : "org.gradle.java.home=${home.replace('\\', '/')}\n"
    }
}
