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

import io.micronaut.graphql.tools.binding.internal.client.ClientDirectories
import io.micronaut.graphql.tools.binding.internal.client.ClientOperations
import io.micronaut.graphql.tools.binding.internal.client.ClientOutcome
import io.micronaut.graphql.tools.binding.internal.client.ClientRequest
import io.micronaut.graphql.tools.binding.internal.client.ClientSchemaBuilder
import io.micronaut.graphql.tools.binding.internal.client.PayloadMode
import io.micronaut.graphql.tools.binding.internal.finding.FindingRenderer
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * The rules of the client: the layout of the client directories, the selection of an operation, the assembly of the operation
 * with the fragments it uses, the validation against the client schema and the diagnostics. A test asserts the codes and the
 * locations, never the message of graphql-java.
 */
class ClientOperationsSpec extends Specification {

    static final String SCHEMA = '''\
type Query {
  book(id: ID!): Book
  books(genre: String): [Book!]!
  search(term: String!): [SearchResult!]!
  version: String
}
type Mutation {
  save(title: String!): Book
}
type Book {
  id: ID!
  title: String
  author: String
}
type Magazine {
  id: ID!
  issue: Int
}
union SearchResult = Book | Magazine
'''

    @TempDir
    Path work

    Path clients
    Path server

    def setup() {
        clients = Files.createDirectories(work.resolve('client'))
        server = Files.createDirectories(work.resolve('server-schema'))
    }

    private Path schemaDir(String name, String sdl = SCHEMA) {
        Path dir = Files.createDirectories(clients.resolve(name))
        Files.writeString(dir.resolve('schema.graphqls'), sdl)
        return dir
    }

    private static void write(Path dir, String file, String text) {
        Path path = dir.resolve(file)
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
    }

    private ClientOperations operations(boolean checks = true, long stack = ClientSchemaBuilder.STACK_BYTES) {
        return new ClientOperations(new ClientDirectories([clients], [server]), checks, stack)
    }

    private static ClientRequest request(String value, String name = '', String schema = 'books', String method = 'book') {
        return new ClientRequest("example.BookClient#${method}()".toString(), method, schema, value, name)
    }

    // the printer of graphql-java spaces a compact document a little differently from one release to the next, the content does not change
    private static String text(ClientOutcome outcome) {
        return outcome.operation().document().replaceAll(/\s+/, '')
    }

    private static String text(String document) {
        return document.replaceAll(/\s+/, '')
    }

    private static List<String> codes(ClientOutcome outcome) {
        return outcome.findings()*.code()
    }

    void "an inline operation is assembled, validated and described"() {
        given:
        schemaDir('books')

        when:
        def outcome = operations().resolve(request('query Book($id: ID!) { book(id: $id) { title } }'))

        then:
        outcome.findings().isEmpty()
        text(outcome) == text('query Book($id: ID!) {book(id: $id) {title}}')
        outcome.operation().operationName() == 'Book'
        outcome.operation().operationType() == 'query'
        outcome.operation().variables() == ['id']
        outcome.operation().payload() == PayloadMode.FIELD
    }

    void "the shorthand query is an anonymous query, and several root fields make the payload the data object"() {
        given:
        schemaDir('books')

        when:
        def outcome = operations().resolve(request('{ version books { id } }'))

        then:
        outcome.operation().operationName() == ''
        outcome.operation().operationType() == 'query'
        outcome.operation().variables() == []
        outcome.operation().payload() == PayloadMode.DATA
    }

    void "a mutation and a subscription keep their type"() {
        given:
        schemaDir('books')

        expect:
        operations().resolve(request('mutation Save($t: String!) { save(title: $t) { id } }')).operation().operationType() == 'mutation'
    }

    void "an alias counts as the response key, so two aliases of one field are two root fields"() {
        given:
        schemaDir('books')

        when:
        def one = operations().resolve(request('{ a: version }')).operation()
        def two = operations().resolve(request('{ a: version b: version }')).operation()

        then:
        one.payload() == PayloadMode.FIELD
        two.payload() == PayloadMode.DATA
    }

    void "root fields reached through a fragment spread and an inline fragment are counted"() {
        given:
        schemaDir('books')

        when:
        def one = operations().resolve(request('{ ...Root } fragment Root on Query { ... on Query { version } }')).operation()
        def two = operations().resolve(request('{ ...Root version } fragment Root on Query { books { id } }')).operation()

        then:
        one.payload() == PayloadMode.FIELD
        two.payload() == PayloadMode.DATA
    }

    void "the used fragments of the document are part of the assembled document, the unused ones are not"() {
        given:
        schemaDir('books')

        when:
        def outcome = operations().resolve(request('''
            query Books { books { ...Titled } }
            fragment Titled on Book { title ...Authored }
            fragment Authored on Book { author }
            fragment Unused on Book { id }
            query Other { version }'''.stripIndent(), 'Books'))

        then:
        outcome.findings().isEmpty()
        text(outcome) == text('query Books {books {...Titled}} fragment Authored on Book {author} fragment Titled on Book {title ...Authored}')
    }

    void "the fragments of the client directory complete an inline document, and the document wins over the directory"() {
        given:
        def dir = schemaDir('books')
        write(dir, 'fragments/book.graphql', 'fragment Titled on Book { title }\nfragment Authored on Book { author }')

        when:
        def outcome = operations().resolve(request('query { books { ...Titled ...Authored } } fragment Titled on Book { id }'))

        then:
        outcome.findings().isEmpty()
        text(outcome) == text('{books {...Titled ...Authored}} fragment Authored on Book {author} fragment Titled on Book {id}')
    }

    void "a fragment that nothing defines is a client-document-invalid at its position, also with the checks off"() {
        given:
        schemaDir('books')

        when:
        def outcome = operations(checks).resolve(request('{ books {\n  ...Missing } }'))

        then:
        codes(outcome) == ['client-document-invalid']
        outcome.findings()[0].documentLocation().path() == null
        outcome.findings()[0].documentLocation().line() == 2
        outcome.findings()[0].documentLocation().column() == 3

        where:
        checks << [true, false]
    }

    void "an operation is selected by name from a file of the client directory, by the value or by the method name"() {
        given:
        def dir = schemaDir('books')
        write(dir, 'ops/book.graphql', 'query FindBook($id: ID!) { book(id: $id) { ...Titled } }\nfragment Titled on Book { title }')

        when:
        def byValue = operations().resolve(request('FindBook'))
        def byMethod = operations().resolve(request('', '', 'books', 'FindBook'))
        def byName = operations().resolve(request('', 'FindBook'))

        then:
        [byValue, byMethod, byName].every { it.findings().isEmpty() }
        [byValue, byMethod, byName].collect { text(it) }.toSet() ==
                [text('query FindBook($id: ID!) {book(id: $id) {...Titled}} fragment Titled on Book {title}')].toSet()
        byValue.operation().variables() == ['id']
    }

    void "a name that no file defines is client-operation-not-found"() {
        given:
        schemaDir('books')

        expect:
        codes(operations().resolve(request('Nothing'))) == ['client-operation-not-found']
        codes(operations().resolve(request('', '', 'books', 'nothing'))) == ['client-operation-not-found']
    }

    void "a name that two files define is client-operation-ambiguous"() {
        given:
        def dir = schemaDir('books')
        write(dir, 'a.graphql', 'query Same { version }')
        write(dir, 'b.graphql', 'query Same { version }')

        expect:
        codes(operations().resolve(request('Same'))) == ['client-operation-ambiguous']
    }

    void "the name member selects one operation of an inline document, and a document of several without it is ambiguous"() {
        given:
        schemaDir('books')
        def document = 'query A { version } query B { books { id } }'

        expect:
        operations().resolve(request(document, 'B')).operation().operationName() == 'B'
        codes(operations().resolve(request(document))) == ['client-operation-ambiguous']
        codes(operations().resolve(request(document, 'C'))) == ['client-operation-not-found']
        codes(operations().resolve(request('fragment F on Book { id }'))) == ['client-operation-not-found']
    }

    void "a syntax error is client-document-invalid with the line and column, inline and in a file"() {
        given:
        def dir = schemaDir('books')
        write(dir, 'bad.graphql', 'query Bad {\n  books { id \n')
        write(dir, 'good.graphql', 'query Good { version }')

        when:
        def inline = operations().resolve(request('query {\n  books { id '))
        def named = operations().resolve(request('Good'))

        then:
        codes(inline) == ['client-document-invalid']
        inline.findings()[0].documentLocation().path() == null
        inline.findings()[0].documentLocation().line() == 2
        codes(named) == ['client-document-invalid']
        named.findings()[0].documentLocation().path() == dir.resolve('bad.graphql').toAbsolutePath().toString()
        named.findings()[0].documentLocation().line() >= 2
    }

    void "a file that does not parse is reported once, by the first request that reads the directory"() {
        given:
        def dir = schemaDir('books')
        write(dir, 'bad.graphql', 'query Bad {')
        write(dir, 'good.graphql', 'query Good { version }')
        def operations = operations()

        expect:
        codes(operations.resolve(request('Good'))) == ['client-document-invalid']
        operations.resolve(request('Good')).operation().operationName() == 'Good'
    }

    void "a document that does not validate against the schema is client-document-invalid at the position of the node"() {
        given:
        schemaDir('books')

        when:
        def outcome = operations().resolve(request('query {\n  books {\n    titel\n  }\n}'))

        then:
        codes(outcome) == ['client-document-invalid']
        outcome.findings()[0].documentLocation().path() == null
        outcome.findings()[0].documentLocation().line() == 3
        outcome.findings()[0].documentLocation().column() == 5
    }

    void "an error of a fragment of a file names the file"() {
        given:
        def dir = schemaDir('books')
        write(dir, 'fragments.graphql', 'fragment Titled on Book {\n  titel\n}')

        when:
        def outcome = operations().resolve(request('{ books { ...Titled } }'))

        then:
        codes(outcome) == ['client-document-invalid']
        outcome.findings()[0].documentLocation().path() == dir.resolve('fragments.graphql').toAbsolutePath().toString()
        outcome.findings()[0].documentLocation().line() == 2
    }

    void "with the checks off the document is not validated, and still assembled and described"() {
        given:
        schemaDir('books')

        when:
        def outcome = operations(false).resolve(request('query Q($x: Int) { notInTheSchema }'))

        then:
        outcome.findings().isEmpty()
        outcome.operation().variables() == ['x']
        outcome.operation().payload() == PayloadMode.FIELD
    }

    void "with the checks off no schema is read: an inline document needs no client directory at all"() {
        given:
        def operations = new ClientOperations(new ClientDirectories([], []), false, ClientSchemaBuilder.STACK_BYTES)

        when:
        def outcome = operations.resolve(request('{ version }'))

        then:
        outcome.findings().isEmpty()
        text(outcome) == '{version}'
        operations.schemasBuilt() == 0
    }

    void "a document with an unparsable text fails also with the checks off"() {
        expect:
        codes(operations(false).resolve(request('{ version'))) == ['client-document-invalid']
    }

    void "a schema that has no directory is client-schema-missing, reported once, with the way out"() {
        given:
        schemaDir('other')
        def operations = operations()

        when:
        def first = operations.resolve(request('{ version }', '', 'books'))
        def second = operations.resolve(request('{ version }', '', 'books'))

        then:
        codes(first) == ['client-schema-missing']
        FindingRenderer.render(first.findings()[0]).contains('fix: add the directory books')
        first.operation() == null
        second.findings().isEmpty()
        second.operation() == null
    }

    void "client directories that are not configured, or do not exist, are client-schema-missing"() {
        expect:
        codes(new ClientOperations(new ClientDirectories([], []), true, 1L << 20).resolve(request('{ version }'))) == ['client-schema-missing']
        codes(new ClientOperations(new ClientDirectories([work.resolve('nothing')], []), true, 1L << 20).resolve(request('{ version }'))) == ['client-schema-missing']
    }

    void "the schema member may be empty when the client directories hold one schema, and not when they hold several"() {
        given:
        schemaDir('books')

        when:
        def one = operations().resolve(request('{ version }', '', ''))
        schemaDir('news')
        def several = operations().resolve(request('{ version }', '', ''))

        then:
        one.findings().isEmpty()
        codes(several) == ['client-schema-missing']
        FindingRenderer.render(several.findings()[0]).contains('[books, news]')
    }

    void "the reserved schema name server reads the schema directories, and its documents are in the subdirectory server"() {
        given:
        write(server, 'schema.graphqls', SCHEMA)
        write(clients.resolve('server'), 'ops.graphql', 'query FindVersion { version }')

        when:
        def inline = operations().resolve(request('{ version }', '', 'server'))
        def named = operations().resolve(request('FindVersion', '', 'server'))

        then:
        inline.findings().isEmpty()
        named.findings().isEmpty()
        named.operation().operationName() == 'FindVersion'
    }

    void "the schema name server without schema directories is client-schema-missing"() {
        expect:
        codes(new ClientOperations(new ClientDirectories([clients], []), true, 1L << 20).resolve(request('{ version }', '', 'server'))) == ['client-schema-missing']
    }

    void "a schema directory without a schema file is client-schema-missing, and a schema that does not parse is client-schema-invalid"() {
        given:
        Files.createDirectories(clients.resolve('empty'))
        schemaDir('broken', 'type Query { a: ')
        schemaDir('dangling', 'type Query { a: Nothing }')

        expect:
        codes(operations().resolve(request('{ a }', '', 'empty'))) == ['client-schema-missing']
        codes(operations().resolve(request('{ a }', '', 'broken'))) == ['client-schema-invalid']
        codes(operations().resolve(request('{ a }', '', 'dangling'))) == ['client-schema-invalid']
    }

    void "the schema is built once for all the operations of a schema, and not at all without checks"() {
        given:
        schemaDir('books')
        def checked = operations()
        def unchecked = operations(false)

        when:
        (1..5).each { checked.resolve(request('{ version }')) }
        (1..5).each { unchecked.resolve(request('{ version }')) }

        then:
        checked.schemasBuilt() == 1
        checked.operations() == 5
        unchecked.schemasBuilt() == 0
    }

    void "the build of a chain of types runs on a stack of its own: a chain that overflows the default stack builds, and a stack too small is a finding"() {
        given:
        def sdl = new StringBuilder('type Query { t0: T0 }\n')
        (0..<3000).each { sdl << "type T${it} { id: ID next: ${it == 2999 ? 'String' : 'T' + (it + 1)} }\n" }
        schemaDir('chain', sdl.toString())

        when:
        def built = operations().resolve(request('{ t0 { id } }', '', 'chain'))
        def small = operations(true, 256L * 1024).resolve(request('{ t0 { id } }', '', 'chain'))

        then:
        built.findings().isEmpty()
        codes(small) == ['client-schema-invalid']
        FindingRenderer.render(small.findings()[0]).contains('fix: shorten the chain')
    }

    void "a system that gives no thread with that stack builds the schema on the calling thread"() {
        given:
        schemaDir('books')

        when:
        def outcome = operations(true, 1L << 62).resolve(request('{ version }'))

        then:
        outcome.findings().isEmpty()
        outcome.operation() != null
    }

    void "a document over the metadata limit is client-document-too-large, and one at the limit passes"() {
        given:
        schemaDir('books')
        when:
        def aliases = { int count -> '{ ' + (0..<count).collect { "a${it}: version" }.join(' ') + ' }' }
        def small = operations(false).resolve(request(aliases.call(10)))
        def huge = operations(false).resolve(request(aliases.call(8000)))

        then:
        small.findings().isEmpty()
        codes(huge) == ['client-document-too-large']
    }

    void "the length of a string constant is counted in modified UTF-8"() {
        expect:
        ClientOperations.modifiedUtf8Length('abc') == 3
        ClientOperations.modifiedUtf8Length('é') == 2
        ClientOperations.modifiedUtf8Length('\u0000') == 2
        ClientOperations.modifiedUtf8Length('€') == 3
        ClientOperations.modifiedUtf8Length(new String(Character.toChars(0x1F600))) == 6
    }

    void "the findings render with the member as the coordinate, the document location and a fix"() {
        given:
        schemaDir('books')

        when:
        def rendered = FindingRenderer.render(operations().resolve(request('query {\n  books {\n    titel\n  }\n}')).findings()[0])

        then:
        rendered.readLines()[0].startsWith('[client-document-invalid] example.BookClient#book(): ')
        rendered.contains('\n  document inline:3:5\n')
        rendered.contains('\n  fix: correct the document at the position that the message gives')
    }
}
