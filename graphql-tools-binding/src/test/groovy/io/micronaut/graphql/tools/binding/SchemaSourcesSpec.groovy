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
package io.micronaut.graphql.tools.binding

import graphql.schema.idl.SchemaParser
import io.micronaut.graphql.tools.binding.internal.schema.SchemaIndex
import io.micronaut.graphql.tools.binding.internal.schema.SchemaLocation
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSourceException
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources.Source
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class SchemaSourcesSpec extends Specification {

    @TempDir
    Path dir

    void "several sources are merged and keep their names in the locations"() {
        when:
        def loaded = SchemaSources.load([new Source('a.graphqls', 'type Query { book: Book }'),
                                         new Source('b/books.graphqls', 'type Book { title: String }')])

        then:
        loaded.read() == ['a.graphqls', 'b/books.graphqls']
        loaded.skipped().isEmpty()
        loaded.registry().getType('Book').get().sourceLocation.sourceName == 'b/books.graphqls'
    }

    void "a source with only executable definitions is skipped, not parsed as a schema"() {
        when:
        def loaded = SchemaSources.load([new Source('schema.graphqls', 'type Query { a: Int }'),
                                         new Source('ops.graphql', 'query A { a } fragment F on Query { a }'),
                                         new Source('empty.graphqls', '  \n')])

        then:
        loaded.read() == ['schema.graphqls']
        loaded.skipped() == ['ops.graphql', 'empty.graphqls']
    }

    void "a source that mixes operations and type definitions fails and names the file"() {
        when:
        SchemaSources.load([new Source('mixed.graphqls', 'type Query { a: Int } query A { a }')])

        then:
        def e = thrown(SchemaSourceException)
        e.message.startsWith('the schema file mixed.graphqls is not valid: ')
    }

    void "a syntax error names the file"() {
        when:
        SchemaSources.load([new Source('broken.graphqls', 'type Query { a: }')])

        then:
        def e = thrown(SchemaSourceException)
        e.message.startsWith('the schema file broken.graphqls is not valid: ')
    }

    void "a type defined twice fails and names the second file"() {
        when:
        SchemaSources.load([new Source('a.graphqls', 'type Query { a: Int }'),
                            new Source('b.graphqls', 'type Query { b: Int }')])

        then:
        def e = thrown(SchemaSourceException)
        e.message.startsWith("the schema file b.graphqls conflicts with [a.graphqls]")
    }

    void "the implicit root types become the schema definition"() {
        given:
        def registry = new SchemaParser().parse('type Query { a: Int } type Mutation { b: Int } type Other { c: Int }')

        expect:
        SchemaSources.implicitRoots(registry) == [mutation: 'Mutation', query: 'Query']

        when:
        SchemaSources.addImplicitRoots(registry)

        then:
        registry.schemaDefinition().get().operationTypeDefinitions*.name == ['query', 'mutation']
    }

    void "a schema block is left alone, and a schema without a root type gets none"() {
        given:
        def explicit = new SchemaParser().parse('schema { query: Root } type Root { a: Int } type Query { a: Int }')
        def none = new SchemaParser().parse('type Other { a: Int }')

        when:
        SchemaSources.addImplicitRoots(explicit)
        SchemaSources.addImplicitRoots(none)

        then:
        explicit.schemaDefinition().get().operationTypeDefinitions*.typeName*.name == ['Root']
        none.schemaDefinition().isEmpty()
    }

    void "a large schema file is parsed with the SDL limits, not the lower limits of a query"() {
        given:
        def text = 'type Query {\n' + (1..6000).collect { "  field$it(a: Int, b: Int): Int" }.join('\n') + '\n}\n'

        expect:
        SchemaSources.load([new SchemaSources.Source('large.graphqls', text)]).registry().getType('Query').isPresent()
    }

    void "a Mutation or Subscription type without a Query type is not a root"() {
        given:
        def registry = new SchemaParser().parse('type Mutation { b: Int } type Subscription { c: Int }')

        expect:
        SchemaSources.implicitRoots(registry).isEmpty()
    }

    void "a file that holds only comments is skipped or named when it fails"() {
        when:
        def loaded = SchemaSources.load([new SchemaSources.Source('comment.graphqls', '# nothing here\n'),
                                         new SchemaSources.Source('a.graphqls', 'type Query { a: Int }')])

        then:
        loaded.read() == ['a.graphqls']
        loaded.skipped() == ['comment.graphqls']
    }

    void "directories are read recursively in sorted order, only the graphqls files"() {
        given:
        Files.createDirectories(dir.resolve('sub'))
        Files.writeString(dir.resolve('b.graphqls'), 'type B { a: Int }')
        Files.writeString(dir.resolve('sub/a.graphqls'), 'type A { a: Int }')
        Files.writeString(dir.resolve('ops.graphql'), 'query { a }')
        Files.writeString(dir.resolve('notes.txt'), 'x')

        expect:
        SchemaSources.readDirectories([dir])*.name() == ['b.graphqls', 'sub/a.graphqls']
    }

    void "a file under two overlapping directories is read once"() {
        given:
        Files.createDirectories(dir.resolve('common'))
        Files.writeString(dir.resolve('common/a.graphqls'), 'type A { a: Int }')

        when:
        def sources = SchemaSources.readDirectories([dir, dir.resolve('common')])

        then:
        sources.size() == 1
        SchemaSources.load(sources).read().size() == 1
    }

    void "a directory that does not exist fails"() {
        when:
        SchemaSources.readDirectories([dir.resolve('missing')])

        then:
        thrown(SchemaSourceException)
    }

    void "a location has a scheme, a root directory and a pattern"() {
        when:
        def location = SchemaLocation.parse(text)

        then:
        location.root() == root
        location.isPattern() == pattern
        location.matches(path) == matches

        where:
        text                                  | root       | pattern | path                  | matches
        'classpath:graphql/**/*.graphqls'     | 'graphql/' | true    | 'graphql/a.graphqls'  | true
        'classpath:graphql/**/*.graphqls'     | 'graphql/' | true    | 'graphql/x/y/a.graphqls' | true
        'classpath:graphql/**/*.graphqls'     | 'graphql/' | true    | 'graphql/a.graphql'   | false
        'classpath*:/schema/*.graphqls'       | 'schema/'  | true    | 'schema/a.graphqls'   | true
        'classpath*:schema/*.graphqls'        | 'schema/'  | true    | 'schema/x/a.graphqls' | false
        'classpath:schema.graphqls'           | ''         | false   | 'schema.graphqls'     | true
        'file:/work/sdl/**/*.graphqls'        | '/work/sdl/' | true  | '/work/sdl/a.graphqls' | true
    }

    void "a file location written with backslashes has the same pattern as one with slashes"() {
        expect:
        SchemaLocation.parse('file:C:\\schemas\\**\\*.graphqls').pattern() == 'C:/schemas/**/*.graphqls'
    }

    void "a location that cannot work fails with a message that says why"() {
        when:
        SchemaLocation.parse(text)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(part)

        where:
        text                  | part
        'graphql/*.graphqls'  | 'must start with'
        'classpath:'          | 'names no path'
        'classpath:**/*.graphqls' | 'no directory before it'
    }

    void "an index is read and written as one path per line"() {
        expect:
        SchemaIndex.parse('# comment\n/graphql/b.graphqls\n\ngraphql/a.graphqls\n') == ['graphql/b.graphqls', 'graphql/a.graphqls']
        SchemaIndex.parse(SchemaIndex.render(['b', 'a'])) == ['a', 'b']
    }
}
