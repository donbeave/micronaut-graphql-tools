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
package io.micronaut.graphql.tools.maven

import org.apache.maven.plugin.descriptor.PluginDescriptor
import org.apache.maven.plugin.testing.MojoRule
import org.apache.maven.plugin.logging.SystemStreamLog
import org.junit.Rule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * What the goal does on a second run: an unchanged run is skipped, a changed input or a new generator version regenerates, and a file
 * that an earlier run generated and the current run no longer emits is deleted. The generator emits no file in this version, so the
 * files of the earlier run are planted through the manifest, as an earlier version of the generator would have left them.
 */
class IncrementalRunSpec extends Specification {

    static final String SCHEMA = 'src/main/resources/graphql/schema.graphqls'

    @Rule
    MojoRule rule = new MojoRule()

    @TempDir
    Path dir

    private List<String> messages = []

    private MojoProject project(String schema = 'type Query { a: String }') {
        def built = MojoProject.create(rule, dir, '<packageName>example.api</packageName>', [(SCHEMA): schema])
        built.mojo.setLog(new SystemStreamLog() {
            @Override
            void info(CharSequence content) {
                messages << content.toString()
            }
        })
        built
    }

    private Path manifest() {
        dir.resolve('target/generated-sources/graphql-tools').resolve(GeneratedSources.MANIFEST)
    }

    private String fingerprint() {
        GeneratedSources.readManifest(dir.resolve('target/generated-sources/graphql-tools')).fingerprint()
    }

    def "an unchanged run is skipped"() {
        given:
        project().mojo.execute()
        def written = Files.getLastModifiedTime(manifest())
        messages.clear()

        when:
        project().mojo.execute()

        then:
        messages.any { it.contains('up to date') }
        Files.getLastModifiedTime(manifest()) == written
    }

    def "a changed schema regenerates"() {
        given:
        project().mojo.execute()
        def before = fingerprint()
        messages.clear()

        when:
        project('type Query { b: String }').mojo.execute()

        then:
        !messages.any { it.contains('up to date') }
        fingerprint() != before
    }

    def "a new generator version with identical inputs regenerates"() {
        given:
        project().mojo.execute()
        def before = fingerprint()
        messages.clear()
        def upgraded = project()
        upgraded.mojo.@plugin = new PluginDescriptor(version: '9.9.9')

        when:
        upgraded.mojo.execute()

        then:
        !messages.any { it.contains('up to date') }
        fingerprint() != before
    }

    def "a changed option regenerates"() {
        given:
        project().mojo.execute()
        def before = fingerprint()

        when:
        MojoProject.create(rule, dir, '<packageName>example.other</packageName>', [(SCHEMA): 'type Query { a: String }']).mojo.execute()

        then:
        fingerprint() != before
    }

    def "a removed schema type leaves no stale source"() {
        given:
        def output = dir.resolve('target/generated-sources/graphql-tools')
        project('type Query { a: String } type Book { id: ID }').mojo.execute()
        GeneratedSources.reconcile(output, fingerprint(), ['java/example/api/model/Book.java': 'class Book {}'])
        assert Files.exists(output.resolve('java/example/api/model/Book.java'))

        when:
        project('type Query { a: String }').mojo.execute()

        then:
        !Files.exists(output.resolve('java/example/api/model/Book.java'))
        !Files.exists(output.resolve('java/example/api/model'))
    }

    def "a deleted generated file regenerates even when the inputs are the same"() {
        given:
        def output = dir.resolve('target/generated-sources/graphql-tools')
        project().mojo.execute()
        GeneratedSources.reconcile(output, fingerprint(), ['java/A.java': 'class A {}'])
        Files.delete(output.resolve('java/A.java'))
        messages.clear()

        when:
        project().mojo.execute()

        then:
        !messages.any { it.contains('up to date') }
    }
}
