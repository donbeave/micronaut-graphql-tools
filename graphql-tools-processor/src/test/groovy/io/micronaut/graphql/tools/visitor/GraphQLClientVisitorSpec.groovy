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
package io.micronaut.graphql.tools.visitor

import spock.lang.Specification

/**
 * The client visitor under javac: the metadata it writes on an operation method, the findings it reports with their codes and
 * locations, what it leaves alone, and the notes. The rules are the ones of {@code ClientOperationsSpec} in the binding module;
 * this specification proves the adapter over the compiler elements. The options and the failure modes that need real
 * {@code -A} arguments are in {@link ProcessorOptionsSpec}.
 */
class GraphQLClientVisitorSpec extends Specification {

    static final String SCHEMA = '''\
type Query {
  book(id: ID!): Book
  books(genre: String): [Book!]!
  version: String
}
type Mutation {
  save(title: String!): Book
}
type Book {
  id: ID!
  title: String
}
'''

    static final String HEADER = '''
package example;
import io.micronaut.graphql.tools.client.annotation.*;
import java.util.*;
'''

    private static JavaParserCompilation compilation(String body, Closure<?> more = null) {
        def compilation = new JavaParserCompilation().clientFile('books/schema.graphqls', SCHEMA)
        compilation.source('example.' + (body =~ /interface\s+(\w+)/)[0][1], HEADER + body)
        return compilation
    }

    private static Map<String, Map<String, Object>> metadata(VisitorCompilation.Result result, String type) {
        return ClientMetadataLoader.read(result.classOutput, type)
    }

    def "an inline operation is written into the annotation metadata of its method, with its variables and payload mode"() {
        when:
        def result = compilation('''
@GraphQLClient(id = "books", schema = "books")
public interface BookClient {
    @GraphQLOperation("query Book($id: ID!) { book(id: $id) { title } }")
    String book(String id);

    @GraphQLOperation("{ version books { id } }")
    String both();

    @GraphQLOperation("mutation Save($title: String!) { save(title: $title) { id } }")
    String save(String title);
}''').compile()

        then:
        result.success
        result.findings == []
        with(metadata(result, 'example.BookClient')) {
            it.keySet() == ['book', 'both', 'save'] as Set
            book.operationName == 'Book'
            book.operationType == 'query'
            book.variables == ['id']
            book.payload == 'field'
            book.document.replaceAll(/\s+/, '') == 'queryBook($id:ID!){book(id:$id){title}}'
            both.operationName == ''
            both.variables == []
            both.payload == 'data'
            save.operationType == 'mutation'
        }
    }

    def "an operation by name, a fragment of a file and the fragments of the document are assembled into one document"() {
        when:
        def result = compilation('''
@GraphQLClient(schema = "books")
public interface BookClient {
    @GraphQLOperation("FindBooks")
    String findBooks();

    @GraphQLOperation("{ books { ...Titled ...Local } } fragment Local on Book { id } fragment Unused on Book { id }")
    String inlineWithFragments();
}''').clientFile('books/operations/books.graphql', 'query FindBooks { books { ...Titled } }')
                .clientFile('books/operations/fragments.graphql', 'fragment Titled on Book { title }').compile()

        then:
        result.success
        with(metadata(result, 'example.BookClient')) {
            findBooks.document.replaceAll(/\s+/, '') == 'queryFindBooks{books{...Titled}}fragmentTitledonBook{title}'
            findBooks.operationName == 'FindBooks'
            inlineWithFragments.document.replaceAll(/\s+/, '') == '{books{...Titled...Local}}fragmentLocalonBook{id}fragmentTitledonBook{title}'
        }
    }

    def "the operation named like the method is used when the annotation names none, and the name member wins"() {
        when:
        def result = compilation('''
@GraphQLClient(schema = "books")
public interface BookClient {
    @GraphQLOperation
    String findBooks();

    @GraphQLOperation(name = "FindBooks")
    String other();
}''').clientFile('books/operations.graphql', 'query findBooks { version } query FindBooks { books { id } }').compile()

        then:
        result.success
        with(metadata(result, 'example.BookClient')) {
            findBooks.operationName == 'findBooks'
            other.operationName == 'FindBooks'
        }
    }

    def "a method without @GraphQLOperation, a type without @GraphQLClient and a client without operations are left alone"() {
        when:
        def result = new JavaParserCompilation()
                .source('example.Plain', HEADER + '''
public interface Plain {
    @GraphQLOperation("{ nothingChecksThis }")
    String operation();
}''')
                .source('example.BookClient', HEADER + '''
@GraphQLClient(schema = "missing")
public interface BookClient {
    String plain();
}''').compile()

        then:
        result.success
        result.findings == []
        result.notes == []
    }

    def "a document that does not validate fails the build with the code, the member and the position in the document"() {
        when:
        def result = compilation('''
@GraphQLClient(schema = "books")
public interface BookClient {
    @GraphQLOperation("query {\\n  books {\\n    titel\\n  }\\n}")
    String books();
}''').compile()

        then:
        !result.success
        result.findings == ['[client-document-invalid] example.BookClient#books()']
        def message = result.errorFor('[client-document-invalid]').getMessage(Locale.ROOT)
        message =~ /\n\s+document inline:3:5\n/
        message =~ /\n\s+fix: correct the document/
    }

    def "every operation of an interface is judged, and each failure is reported on its own method"() {
        when:
        def result = compilation('''
@GraphQLClient(schema = "books")
public interface BookClient {
    @GraphQLOperation("{ nope }")
    String first();

    @GraphQLOperation("{ version }")
    String fine();

    @GraphQLOperation("{ alsoNope }")
    String second(String argument, int[] numbers);
}''').compile()

        then:
        !result.success
        result.findings == ['[client-document-invalid] example.BookClient#first()',
                            '[client-document-invalid] example.BookClient#second(java.lang.String,int[])']
    }

    def "a client schema that does not exist is client-schema-missing, once, naming the way out"() {
        when:
        def result = compilation('''
@GraphQLClient(schema = "nowhere")
public interface BookClient {
    @GraphQLOperation("{ version }")
    String a();

    @GraphQLOperation("{ version }")
    String b();
}''').compile()

        then:
        !result.success
        result.findings == ['[client-schema-missing] example.BookClient#a()']
        result.errors[0].contains('fix: add the directory nowhere')
    }

    def "an operation selected by an unknown name or by several is reported with its code"() {
        when:
        def result = compilation('''
@GraphQLClient(schema = "books")
public interface BookClient {
    @GraphQLOperation("Nothing")
    String missing();

    @GraphQLOperation("query A { version } query B { version }")
    String several();
}''').compile()

        then:
        result.findings == ['[client-operation-not-found] example.BookClient#missing()',
                            '[client-operation-ambiguous] example.BookClient#several()']
    }

    def "a document of a file that just fits in the metadata is written and read back, and one that is longer is not"() {
        given:
        def document = { int length ->
            def fields = new StringBuilder()
            int i = 0
            while (fields.length() < length) {
                fields << String.format('f%05d ', i++)
            }
            return "query Huge { ${fields} }".toString()
        }
        def client = '''
@GraphQLClient(schema = "books")
public interface BookClient {
    @GraphQLOperation("Huge")
    String huge();
}'''

        when:
        def fits = compilation(client).clientFile('books/huge.graphql', document(65400)).option(ProcessorOptions.CLIENT_CHECKS, 'false').compile()
        def over = compilation(client).clientFile('books/huge.graphql', document(65700)).option(ProcessorOptions.CLIENT_CHECKS, 'false').compile()

        then:
        fits.success
        metadata(fits, 'example.BookClient').huge.document.length() > 65_400
        over.findings == ['[client-document-too-large] example.BookClient#huge()']
    }

    def "the server schema is the schema directory of the resolvers"() {
        when:
        def result = new JavaParserCompilation().schema('schema.graphqls', SCHEMA)
                .source('example.ServerClient', HEADER + '''
@GraphQLClient(schema = "server")
public interface ServerClient {
    @GraphQLOperation("{ version }")
    String version();

    @GraphQLOperation("{ notThere }")
    String broken();
}''').compile()

        then:
        result.findings == ['[client-document-invalid] example.ServerClient#broken()']
    }

    def "with the client checks off the documents are assembled but not validated, and one note says so"() {
        when:
        def result = compilation('''
@GraphQLClient(schema = "books")
public interface BookClient {
    @GraphQLOperation("query Q($x: Int) { notInTheSchema }")
    String a();

    @GraphQLOperation("{ alsoNotInTheSchema }")
    String b();
}''').option(ProcessorOptions.CLIENT_CHECKS, 'false').compile()

        then:
        result.success
        result.notes == ['[graphql-tools] GraphQL client checks are off (micronaut.graphql.tools.processor.client.checks=false): '
                         + '2 client operations were parsed and assembled, and not validated against a schema.']
        with(metadata(result, 'example.BookClient')) {
            a.variables == ['x']
            a.payload == 'field'
        }
    }

    def "the checks=false of the whole compilation also turns the client checks off"() {
        when:
        def result = compilation('''
@GraphQLClient(schema = "books")
public interface BookClient {
    @GraphQLOperation("{ notInTheSchema }")
    String a();
}''').option(ProcessorOptions.CHECKS, 'false').compile()

        then:
        result.success
        result.notes.any { it.contains('GraphQL client checks are off (micronaut.graphql.tools.processor.checks=false): 1 client operation was parsed') }
    }

    def "a document that cannot be parsed fails the build also with the checks off"() {
        when:
        def result = compilation('''
@GraphQLClient(schema = "books")
public interface BookClient {
    @GraphQLOperation("{ version")
    String a();
}''').option(ProcessorOptions.CLIENT_CHECKS, 'false').compile()

        then:
        result.findings == ['[client-document-invalid] example.BookClient#a()']
    }

    def "a passing compilation says how many operations were checked against how many schemas"() {
        when:
        def result = compilation('''
@GraphQLClient(schema = "books")
public interface BookClient {
    @GraphQLOperation("{ version }")
    String a();

    @GraphQLOperation("{ books { id } }")
    String b();
}''').compile()

        then:
        result.success
        result.notes == ['[graphql-tools] checked 2 client operations against 1 client schema.']
    }

    def "a compilation without a client reads no client directory and prints no note"() {
        when:
        def result = new VisitorCompilation().option(ProcessorOptions.CLIENT_DIRS, '/no/such/directory')
                .source('example.Plain', 'package example; public class Plain { }').compile()

        then:
        result.success
        result.notes == []
    }
}
