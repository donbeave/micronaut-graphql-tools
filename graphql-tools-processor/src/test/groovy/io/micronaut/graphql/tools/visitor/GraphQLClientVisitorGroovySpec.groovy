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

import io.micronaut.graphql.tools.corpus.GroovyCaseCompiler
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * The client visitor under the Groovy compiler, in process: Micronaut's AST transformation runs it, the options are system
 * properties, and the metadata it writes is on the bean definition of the compiled interface. Kotlin under KSP is the
 * client corpus of {@code corpusKotlin}.
 */
class GraphQLClientVisitorGroovySpec extends Specification {

    static final String SCHEMA = 'type Query {\n  book(id: ID!): Book\n  version: String\n}\ntype Book {\n  id: ID!\n  title: String\n}\n'

    @TempDir
    Path work

    private List<Object> compile(String source, Map<String, String> options = [:], Map<String, String> clientFiles = [:]) {
        Path client = Files.createDirectories(work.resolve('client/books'))
        Files.writeString(client.resolve('schema.graphqls'), SCHEMA)
        clientFiles.each { name, text -> Files.writeString(client.resolve(name), text) }
        Path sources = Files.createDirectories(work.resolve('src'))
        Files.writeString(sources.resolve('BookClient.groovy'), source)
        Path classes = Files.createDirectories(work.resolve('classes'))
        Map<String, String> all = [(ProcessorOptions.CLIENT_DIRS): work.resolve('client').toString()] + options
        return GroovyCaseCompiler.run([sources.resolve('BookClient.groovy').toString()], classes.toString(), [], all)
    }

    def "an operation is written into the annotation metadata of its method under the Groovy compiler"() {
        when:
        def result = compile('''
package example
import io.micronaut.graphql.tools.client.annotation.*
@GraphQLClient(id = 'books', schema = 'books')
interface BookClient {
    @GraphQLOperation('query Book($id: ID!) { book(id: $id) { title } }')
    Object book(String id)

    @GraphQLOperation('FindVersion')
    Object version()
}''', [:], ['ops.graphql': 'query FindVersion { version }'])

        then:
        result[0]
        with(ClientMetadataLoader.read(work.resolve('classes').toFile(), 'example.BookClient')) {
            book.operationName == 'Book'
            book.variables == ['id']
            book.payload == 'field'
            book.document.replaceAll(/\s+/, '') == 'queryBook($id:ID!){book(id:$id){title}}'
            version.document.replaceAll(/\s+/, '') == 'queryFindVersion{version}'
        }
    }

    def "a document that does not validate fails the Groovy compilation with the code and the position"() {
        when:
        def result = compile('''
package example
import io.micronaut.graphql.tools.client.annotation.*
@GraphQLClient(schema = 'books')
interface BookClient {
    @GraphQLOperation('query {\\n  books {\\n    id\\n  }\\n}')
    Object books()
}''')

        then:
        !result[0]
        (result[1] as List<String>).any { it.contains('[client-document-invalid] example.BookClient#books()') }
        (result[1] as List<String>).any { it =~ /document inline:2:3/ }
    }

    def "with the client checks off the Groovy compiler assembles the documents and prints one note"() {
        when:
        def result = compile('''
package example
import io.micronaut.graphql.tools.client.annotation.*
@GraphQLClient(schema = 'books')
interface BookClient {
    @GraphQLOperation('{ notInTheSchema }')
    Object missing()
}''', [(ProcessorOptions.CLIENT_CHECKS): 'false'])

        then:
        result[0]
        ClientMetadataLoader.read(work.resolve('classes').toFile(), 'example.BookClient').missing.payload == 'field'
    }
}
