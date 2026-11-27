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

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

/**
 * The cache and output scenarios of the generation task: a relocated project directory keeps the cache key, a removed schema type
 * removes its file, the output does not depend on the machine, and a synthetic schema reports its cold and warm generation time.
 */
class GenerateCacheFunctionalSpec extends PluginFunctionalSpec {

    private static final String GENRE = '\nenum Genre {\n  FANTASY\n  HORROR\n}\n'

    @Unroll
    def "a project directory that moved still hits the build cache on Gradle #gradle"() {
        given:
        settings()
        schema(SCHEMA + GENRE)
        write('build.gradle.kts', kotlinBuild())
        assert build(gradle, 'generateGraphQL').task(':generateGraphQL').outcome == TaskOutcome.SUCCESS
        def originalDirectory = project
        project = Files.createTempDirectory('relocated-project')

        when:
        settings()
        schema(SCHEMA + GENRE)
        write('build.gradle.kts', kotlinBuild())
        def outcome = build(gradle, 'generateGraphQL').task(':generateGraphQL').outcome

        then:
        project != originalDirectory
        outcome == TaskOutcome.FROM_CACHE
        exists('build/generated/sources/graphqlTools/main/example/model/Genre.java')

        cleanup:
        project.toFile().deleteDir()

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "a schema type that the schema no longer has loses its generated file on Gradle #gradle"() {
        given:
        settings()
        schema(SCHEMA + GENRE)
        write('build.gradle.kts', kotlinBuild())
        build(gradle, 'generateGraphQL')
        assert exists('build/generated/sources/graphqlTools/main/example/model/Genre.java')

        when:
        schema()
        def outcome = build(gradle, 'generateGraphQL').task(':generateGraphQL').outcome

        then:
        outcome == TaskOutcome.SUCCESS
        !exists('build/generated/sources/graphqlTools/main/example/model/Genre.java')

        where:
        gradle << GRADLE_VERSIONS
    }

    @Unroll
    def "two projects in different directories generate the same bytes on Gradle #gradle"() {
        given:
        settings()
        schema(SCHEMA + GENRE)
        write('build.gradle.kts', kotlinBuild())
        build(gradle, 'generateGraphQL')
        def first = snapshot('build/generated/sources/graphqlTools/main') + snapshot('build/generated/resources/graphqlTools/main')
        project = Files.createTempDirectory('second-machine')
        buildCache = Files.createTempDirectory('second-cache')

        when:
        settings()
        schema(SCHEMA + GENRE)
        write('build.gradle.kts', kotlinBuild())
        def outcome = build(gradle, 'generateGraphQL').task(':generateGraphQL').outcome

        then:
        outcome == TaskOutcome.SUCCESS
        !first.isEmpty()
        snapshot('build/generated/sources/graphqlTools/main') + snapshot('build/generated/resources/graphqlTools/main') == first

        cleanup:
        project.toFile().deleteDir()
        buildCache.toFile().deleteDir()

        where:
        gradle << GRADLE_VERSIONS
    }

    def "cold, warm and cached generation of a synthetic schema are reported on the current Gradle"() {
        given:
        def types = 300
        def text = new StringBuilder('type Query {\n  node(id: ID!): Node0\n}\n')
        (0..<types).each { i ->
            text << "\ntype Node$i {\n  id: ID!\n  kind: Kind$i\n  next: Node${(i + 1) % types}\n}\n"
            text << "\nenum Kind$i {\n  A\n  B\n  C\n  D\n  E\n}\n"
        }
        settings()
        schema(text.toString())
        write('build.gradle.kts', kotlinBuild())

        when:
        def cold = timed { build(CURRENT, 'generateGraphQL') }
        def warm = timed { build(CURRENT, 'generateGraphQL') }
        def noop = timed { build(CURRENT, 'help') }
        build(CURRENT, 'clean')
        def cached = timed { build(CURRENT, 'generateGraphQL') }

        then:
        println("SYNTHETIC types=$types cold=${cold.ms} ms (${cold.outcome}) up-to-date=${warm.ms} ms (${warm.outcome}) " +
                "baseline-help=${noop.ms} ms from-cache=${cached.ms} ms (${cached.outcome})")
        cold.outcome == TaskOutcome.SUCCESS
        warm.outcome == TaskOutcome.UP_TO_DATE
        cached.outcome == TaskOutcome.FROM_CACHE
    }

    private Map<String, String> snapshot(String directory) {
        Path root = project.resolve(directory)
        if (!Files.exists(root)) {
            return [:]
        }
        Files.walk(root).withCloseable { stream ->
            stream.filter { Files.isRegularFile(it) }.collect(Collectors.toMap(
                    { root.relativize(it).toString() }, { new String(Files.readAllBytes(it), 'UTF-8') }))
        }
    }

    private Map timed(Closure<org.gradle.testkit.runner.BuildResult> run) {
        long start = System.nanoTime()
        def result = run()
        [ms: (System.nanoTime() - start).intdiv(1_000_000), outcome: result.task(':generateGraphQL')?.outcome]
    }
}
