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

import io.micronaut.graphql.tools.codegen.Diagnostic
import io.micronaut.graphql.tools.codegen.GraphQLCodeGenerator
import io.micronaut.graphql.tools.codegen.Mode
import io.micronaut.graphql.tools.codegen.Options
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

/**
 * The discovery of the operation documents of client mode, and the diagnostics about the documents.
 */
class ClientDocumentsSpec extends Specification {

    static final String SCHEMA = '''
        type User { id: ID!  name: String! }
        type Query { user: User!  users: [User!]! }
    '''

    @TempDir
    Path directory

    static Options.Builder client() {
        Options.builder().mode(Mode.CLIENT).packageName('com.example')
    }

    private Path write(String path, String text) {
        def file = directory.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        file
    }

    private static IrResult sources(List<SchemaSource> documents, Options options = client().build()) {
        IrBuilder.fromSources([new SchemaSource('schema.graphqls', SCHEMA)], options, [], OperationInput.sources(documents))
    }

    private static IrResult one(String document) {
        sources([new SchemaSource('ops.graphql', document)])
    }

    def "a directory is read with operationIncludes and operationExcludes, in the order of the relative paths"() {
        given:
        write('schema.graphqls', SCHEMA)
        write('z.graphql', 'query Z { users { id } }')
        write('a/b.graphql', 'query B { users { id } }')
        write('a/draft/c.graphql', 'query C { users { id } }')
        write('notes.txt', 'query Nope {')
        write('other/d.gql', 'query D { users { id } }')

        when:
        def all = IrBuilder.fromDirectory(directory, client().build(), [], OperationInput.directory(directory))
        def narrowed = IrBuilder.fromDirectory(directory, client().operationExcludes(['**/draft/**']).build(), [], OperationInput.directory(directory))
        def custom = IrBuilder.fromDirectory(directory, client().operationIncludes(['**/*.gql']).build(), [], OperationInput.directory(directory))

        then:
        all.diagnostics().empty
        all.ir().operations()*.names()*.sdlName() == ['B', 'C', 'Z']
        all.ir().operations()*.origin()*.path() == ['a/b.graphql', 'a/draft/c.graphql', 'z.graphql']
        narrowed.ir().operations()*.names()*.sdlName() == ['B', 'Z']
        custom.ir().operations()*.names()*.sdlName() == ['D']
    }

    def "the shorter forms read the operation documents from the schema directory"() {
        given:
        write('schema.graphqls', SCHEMA)
        write('ops/q.graphql', 'query Q { users { id } }')

        expect:
        IrBuilder.fromDirectory(directory, client().build()).ir().operations()*.names()*.sdlName() == ['Q']
    }

    def "files are read in the order of their paths, relative to the closest directory that holds them"() {
        given:
        def schema = write('schema.graphqls', SCHEMA)
        def b = write('x/b.graphql', 'query B { users { id } }')
        def a = write('x/a.graphql', 'query A { users { id } }')

        when:
        def result = IrBuilder.fromFiles([schema], client().build(), [], OperationInput.files([b, a]))

        then:
        result.diagnostics().empty
        result.ir().operations()*.names()*.sdlName() == ['A', 'B']
        result.ir().operations()*.origin()*.path() == ['a.graphql', 'b.graphql']
    }

    def "the generator takes the documents as a directory, as files or as text, and reads the schema directory without any of them"() {
        given:
        write('schema/schema.graphqls', SCHEMA)
        write('schema/q.graphql', 'query FromSchemaDirectory { users { id } }')
        write('ops/o.graphql', 'query FromOperationDirectory { users { id } }')
        def builder = { GraphQLCodeGenerator.builder().withSchemaDirectory(directory.resolve('schema')).withOutputDirectory(directory.resolve('out'))
                .withOptions(client().build()) }

        expect:
        builder().build().toIr().ir().operations()*.names()*.sdlName() == ['FromSchemaDirectory']
        builder().withOperationDirectory(directory.resolve('ops')).build().toIr().ir().operations()*.names()*.sdlName() ==
                ['FromOperationDirectory']
        builder().withOperationFiles(directory.resolve('ops/o.graphql')).build().toIr().ir().operations()*.names()*.sdlName() ==
                ['FromOperationDirectory']
        builder().withOperationSources([new SchemaSource('t.graphql', 'query FromText { users { id } }')]).build().toIr().ir()
                .operations()*.names()*.sdlName() == ['FromText']
    }

    def "the three forms of operation input are alternatives"() {
        when:
        GraphQLCodeGenerator.builder().withOutputDirectory(directory).withOptions(client().build())
                .withOperationDirectory(directory).withOperationFiles(directory.resolve('a.graphql')).build()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('alternatives')
    }

    def "client mode generates the enums that an operation reaches"() {
        given:
        write('schema/schema.graphqls', 'enum Role { A B }\ntype Query { users(role: Role): [String!]! }')
        write('schema/q.graphql', 'query Q($role: Role) { users(role: $role) }')

        when:
        def result = GraphQLCodeGenerator.builder().withSchemaDirectory(directory.resolve('schema'))
                .withOutputDirectory(directory.resolve('out')).withOptions(client().build()).build().generate()

        then:
        result.diagnostics().empty
        result.files()*.path().contains('com/example/model/Role.java')
    }

    def "the type mappings file of a client lists the enums, inputs and scalars, and not the result models"() {
        given:
        write('schema/schema.graphqls', 'scalar Stamp\nenum Role { A B }\ninput In { r: Role  s: Stamp }\ntype User { id: ID! }\n'
                + 'type Query { user(in: In): User }')
        write('schema/q.graphql', 'query Q($in: In) { user(in: $in) { id } }')

        when:
        def result = GraphQLCodeGenerator.builder().withSchemaDirectory(directory.resolve('schema'))
                .withOutputDirectory(directory.resolve('out')).withOptions(client().typeMappings([Stamp: 'java.time.Instant']).build())
                .build().generate()
        def mappings = result.files().find { it.path().endsWith('type-mappings.properties') }.content()

        then:
        result.diagnostics().empty
        mappings.readLines().findAll { !it.startsWith('#') } == ['In=com.example.model.In', 'Role=com.example.model.Role',
                                                                 'Stamp=java.time.Instant']
    }

    def "client mode without a document is an error, and so is a directory without a match"() {
        given:
        write('schema.graphqls', SCHEMA)

        expect:
        IrBuilder.fromSources([new SchemaSource('schema.graphqls', SCHEMA)], client().build()).diagnostics()*.code() ==
                [Diagnostic.NO_OPERATION_DOCUMENTS]
        IrBuilder.fromDirectory(directory, client().operationIncludes(['**/*.gql']).build()).diagnostics()*.code() ==
                [Diagnostic.NO_OPERATION_DOCUMENTS]
        IrBuilder.fromDirectory(directory, client().operationIncludes(['**/*.gql']).build()).diagnostics()[0].optionName() == 'operationIncludes'
        one('# only a comment').diagnostics()*.code() == [Diagnostic.NO_OPERATION_DOCUMENTS]
        one('fragment F on User { id }').diagnostics()*.code() == [Diagnostic.NO_OPERATION_DOCUMENTS]
    }

    def "an operation directory that does not exist is an error"() {
        expect:
        IrBuilder.fromSources([new SchemaSource('schema.graphqls', SCHEMA)], client().build(), [],
                OperationInput.directory(directory.resolve('missing'))).diagnostics()*.code() == [Diagnostic.OPERATION_FILE_UNREADABLE]
    }

    @Unroll
    def "#name is reported as #code"() {
        when:
        def result = sources(documents)

        then:
        result.hasErrors()
        result.ir().operations().empty
        result.diagnostics()*.code() == [code]
        result.diagnostics()[0].message().contains(text)

        where:
        name                                | documents                                                                       | code                              | text
        'an operation without a name'       | [doc('query { users { id } }')]                                                 | Diagnostic.ANONYMOUS_OPERATION    | 'has no name'
        'an operation defined twice'        | [doc('query A { users { id } }'), doc('query A { user { id } }', 'b.graphql')]   | Diagnostic.DUPLICATE_OPERATION    | "'A' is defined twice"
        'a fragment defined twice'          | [doc('query A { user { ...F } } fragment F on User { id } fragment F on User { name }')] | Diagnostic.DUPLICATE_FRAGMENT | "'F' is defined twice"
        'a fragment that nothing defines'   | [doc('query A { user { ...Missing } }')]                                         | Diagnostic.OPERATION_INVALID      | 'Missing'
        'a field the schema does not have'  | [doc('query A { user { nope } }')]                                               | Diagnostic.OPERATION_INVALID      | 'nope'
        'a variable that nothing uses'      | [doc('query A($x: String) { user { id } }')]                                        | Diagnostic.OPERATION_INVALID      | 'Unused variable'
        'a document that does not parse'    | [doc('query A { user { id ')]                                                    | Diagnostic.OPERATION_SYNTAX_ERROR | 'ops.graphql'
        'an alias of __typename'            | [doc('query A { user { __typename: name } }')]                                   | Diagnostic.RESERVED_TYPENAME_ALIAS | 'reserved'
    }

    def "a document that is too large for a string constant is an error"() {
        given:
        def fields = (1..4000).collect { "field$it: user { id name }" }.join(' ')

        when:
        def result = one("query Big { $fields }")

        then:
        result.diagnostics()*.code() == [Diagnostic.DOCUMENT_TOO_LARGE]
        result.diagnostics()[0].message().contains('65535')
    }

    def "every problem of the documents is reported in one run"() {
        when:
        def result = sources([doc('query A { nope }'), doc('query B { alsoNope }', 'b.graphql'), doc('query { user { id } }', 'c.graphql')])

        then:
        result.diagnostics()*.code() == [Diagnostic.ANONYMOUS_OPERATION, Diagnostic.OPERATION_INVALID, Diagnostic.OPERATION_INVALID]
    }

    def "the same path given twice with different content is an error, and with the same content is read once"() {
        expect:
        sources([doc('query A { user { id } }'), doc('query A { users { id } }')]).diagnostics()*.code() ==
                [Diagnostic.DUPLICATE_DEFINITION]
        sources([doc('query A { user { id } }'), doc('query A { user { id } }')]).diagnostics().empty
    }

    def "operationIncludes must hold a glob"() {
        expect:
        client().operationIncludes([]).build().validate()*.optionName() == ['operationIncludes']
        client().operationIncludes([' ']).build().validate()*.optionName() == ['operationIncludes.0']
        client().operationExcludes(['']).build().validate()*.optionName() == ['operationExcludes.0']
    }

    def "an error in the schema is reported once and the operations are not lowered"() {
        when:
        def result = IrBuilder.fromSources([new SchemaSource('schema.graphqls', 'type Query { user: Missing }')], client().build(), [],
                OperationInput.sources([doc('query A { user { id } }')]))

        then:
        result.hasErrors()
        result.ir().operations().empty
    }

    private static SchemaSource doc(String text, String path = 'ops.graphql') {
        new SchemaSource(path, text)
    }
}
