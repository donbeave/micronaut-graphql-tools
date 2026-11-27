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

import io.micronaut.graphql.tools.codegen.Options
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Which files of the schema directory the globs select, and in which order.
 */
class SchemaFilesSpec extends Specification {

    @TempDir
    Path dir

    private void touch(String... names) {
        names.each {
            Files.createDirectories(dir.resolve(it).parent)
            Files.writeString(dir.resolve(it), 'type Query { a: String }')
        }
    }

    private List<String> select(List<String> includes, List<String> excludes = []) {
        SchemaFiles.select(dir, includes, excludes).collect { SchemaFiles.relative(dir, it) }
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

    def "an include that is not a default selects other extensions"() {
        given:
        touch('a.gql', 'b.graphqls')

        expect:
        select(['**/*.gql']) == ['a.gql']
    }

    def "the order is the UTF-8 byte order of the relative path"() {
        given:
        touch('c.graphqls', 'Z.graphqls', 'a/x.graphqls', 'a.graphqls')

        expect:
        select(Options.DEFAULT_INCLUDES) == ['Z.graphqls', 'a.graphqls', 'a/x.graphqls', 'c.graphqls']
    }
}
