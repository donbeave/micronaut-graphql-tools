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

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * The reconciliation of the output directory and the fingerprint that decides whether a run can be skipped.
 */
class GeneratedSourcesSpec extends Specification {

    @TempDir
    Path out

    @TempDir
    Path schema

    def "the first run writes every file and the manifest"() {
        when:
        def outcome = GeneratedSources.reconcile(out, 'f1', ['java/a/A.java': 'class A {}', 'java/a/B.java': 'class B {}'])

        then:
        outcome == new GeneratedSources.Outcome(2, 0, 0)
        Files.readString(out.resolve('java/a/A.java')) == 'class A {}'
        GeneratedSources.readManifest(out) == new GeneratedSources.Manifest('f1', ['java/a/A.java', 'java/a/B.java'])
    }

    def "a removed schema type leaves no stale source and no empty directory"() {
        given:
        GeneratedSources.reconcile(out, 'f1', ['java/a/A.java': 'class A {}', 'java/b/B.java': 'class B {}'])

        when:
        def outcome = GeneratedSources.reconcile(out, 'f2', ['java/a/A.java': 'class A {}'])

        then:
        outcome == new GeneratedSources.Outcome(0, 1, 1)
        !Files.exists(out.resolve('java/b/B.java'))
        !Files.exists(out.resolve('java/b'))
        Files.exists(out.resolve('java/a/A.java'))
    }

    def "an unchanged file keeps its modification time and a changed one is rewritten"() {
        given:
        GeneratedSources.reconcile(out, 'f1', ['java/A.java': 'class A {}', 'java/B.java': 'class B {}'])
        def a = out.resolve('java/A.java')
        Files.setLastModifiedTime(a, java.nio.file.attribute.FileTime.fromMillis(1_000_000))

        when:
        def outcome = GeneratedSources.reconcile(out, 'f2', ['java/A.java': 'class A {}', 'java/B.java': 'class B { int x; }'])

        then:
        outcome == new GeneratedSources.Outcome(1, 1, 0)
        Files.getLastModifiedTime(a).toMillis() == 1_000_000
        Files.readString(out.resolve('java/B.java')) == 'class B { int x; }'
    }

    def "a file that the manifest does not list is never deleted"() {
        given:
        Files.writeString(out.resolve('Mine.java'), 'class Mine {}')
        GeneratedSources.reconcile(out, 'f1', ['java/A.java': 'class A {}'])

        when:
        GeneratedSources.reconcile(out, 'f2', [:])

        then:
        Files.exists(out.resolve('Mine.java'))
        !Files.exists(out.resolve('java/A.java'))
    }

    def "a manifest entry outside the output directory is ignored"() {
        given:
        def outside = Files.writeString(out.parent.resolve('outside-' + out.fileName + '.txt'), 'keep')
        Files.writeString(out.resolve(GeneratedSources.MANIFEST), "fingerprint=f1\nfile=../${outside.fileName}\nfile=/etc/hosts\n")

        when:
        GeneratedSources.reconcile(out, 'f2', [:])

        then:
        Files.exists(outside)
        !GeneratedSources.isUpToDate(out, 'f1')

        cleanup:
        Files.deleteIfExists(outside)
    }

    def "a generated path that leaves the output directory is rejected"() {
        when:
        GeneratedSources.reconcile(out, 'f1', ['../Escape.java': 'class Escape {}'])

        then:
        thrown(IllegalArgumentException)
    }

    def "a run is up to date only for the same fingerprint and while every file exists"() {
        given:
        GeneratedSources.reconcile(out, 'f1', ['java/A.java': 'class A {}'])

        expect:
        GeneratedSources.isUpToDate(out, 'f1')
        !GeneratedSources.isUpToDate(out, 'f2')

        when:
        Files.delete(out.resolve('java/A.java'))

        then:
        !GeneratedSources.isUpToDate(out, 'f1')
    }

    def "there is no previous run without a manifest"() {
        expect:
        !GeneratedSources.isUpToDate(out, 'f1')
        GeneratedSources.readManifest(out) == null
    }

    def "the fingerprint follows the version, the options, and the path and content of each schema file"() {
        given:
        def file = Files.writeString(schema.resolve('a.graphqls'), 'type Query { a: String }')
        def base = GeneratedSources.fingerprint('1.0.0', [packageName: 'x'], schema, [file])

        expect:
        base == GeneratedSources.fingerprint('1.0.0', [packageName: 'x'], schema, [file])
        base != GeneratedSources.fingerprint('1.0.1', [packageName: 'x'], schema, [file])
        base != GeneratedSources.fingerprint('1.0.0', [packageName: 'y'], schema, [file])
        base != GeneratedSources.fingerprint('1.0.0', [packageName: 'x', kotlinSuspend: 'true'], schema, [file])

        when:
        Files.writeString(file, 'type Query { b: String }')

        then:
        base != GeneratedSources.fingerprint('1.0.0', [packageName: 'x'], schema, [file])
    }

    def "the fingerprint tells a moved schema file and a split of the same bytes apart"() {
        given:
        def a = Files.writeString(schema.resolve('a.graphqls'), 'ab')
        def b = Files.writeString(schema.resolve('b.graphqls'), 'c')
        def c = Files.writeString(schema.resolve('c.graphqls'), 'abc')

        expect:
        GeneratedSources.fingerprint('1', [:], schema, [a, b]) != GeneratedSources.fingerprint('1', [:], schema, [c])
    }
}
