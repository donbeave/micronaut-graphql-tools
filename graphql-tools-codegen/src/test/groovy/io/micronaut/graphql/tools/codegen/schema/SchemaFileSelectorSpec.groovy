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
package io.micronaut.graphql.tools.codegen.schema

import io.micronaut.graphql.tools.codegen.Options
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Which files of a schema directory the globs select, and in which order.
 */
class SchemaFileSelectorSpec extends Specification {

    @TempDir
    Path dir

    private void touch(String... names) {
        names.each {
            Files.createDirectories(dir.resolve(it).parent)
            Files.writeString(dir.resolve(it), 'type Query { a: String }')
        }
    }

    private List<String> select(List<String> includes, List<String> excludes = []) {
        SchemaFileSelector.select(dir, includes, excludes).collect { SchemaFileSelector.relative(dir, it) }
    }

    def "the default globs select a file of the root and of a subdirectory, in path order"() {
        given:
        touch('z.graphqls', 'a/b.graphql', 'a.graphqls', 'notes.txt', 'a/deep/c.graphqls')

        expect:
        select(Options.DEFAULT_INCLUDES) == ['a.graphqls', 'a/b.graphql', 'a/deep/c.graphqls', 'z.graphqls']
    }

    def "an exclude removes files from the selection"() {
        given:
        touch('a.graphqls', 'draft/b.graphqls', 'c.graphqls')

        expect:
        select(Options.DEFAULT_INCLUDES, ['draft/**', 'c.*']) == ['a.graphqls']
    }

    def "the order is the UTF-8 byte order of the relative path"() {
        given:
        touch('c.graphqls', 'Z.graphqls', 'a/x.graphqls', 'a.graphqls')

        expect:
        select(Options.DEFAULT_INCLUDES) == ['Z.graphqls', 'a.graphqls', 'a/x.graphqls', 'c.graphqls']
    }

    def "brackets are literal and a directory wildcard in the middle matches no directory"() {
        given:
        touch('a/b[1].graphqls', 'a/b1.graphqls', 'a/x/y/b1.graphqls')

        expect:
        select(['a/**/b[1].graphqls']) == ['a/b[1].graphqls']
        select(['a/**/b1.graphqls']) == ['a/b1.graphqls', 'a/x/y/b1.graphqls']
    }

    def "an unreadable subdirectory is an UncheckedIOException"() {
        given:
        touch('a.graphqls', 'locked/b.graphqls')
        def locked = dir.resolve('locked')
        Files.setPosixFilePermissions(locked, [] as Set)
        org.junit.Assume.assumeFalse('an owner who can list anything', Files.isReadable(locked))

        when:
        SchemaFileSelector.select(dir, Options.DEFAULT_INCLUDES, [])

        then:
        thrown(UncheckedIOException)

        cleanup:
        Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString('rwx------'))
    }
}
