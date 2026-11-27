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

import groovy.transform.CompileStatic

/**
 * The compile-time harness of the client codes: each scenario compiles a small client interface and returns the message of
 * its compile error, as {@link ClientErrorReferenceSpec} compares it with the example of the error reference. The name of a
 * scenario is what the page writes after {@code // client: }.
 */
@CompileStatic
final class ClientCodeScenarios {

    static final String SCHEMA = 'type Query {\n  book(id: ID!): Book\n  at(moment: DateTime): String\n  version: String\n}\nscalar DateTime\ntype Mutation {\n  save(title: String!): Book\n}\ntype Book {\n  id: ID!\n  title: String\n}\n'

    /** The scenarios and the code that each one raises. */
    static final Map<String, String> CODES = [
            'schema-missing'     : 'client-schema-missing',
            'schema-invalid'     : 'client-schema-invalid',
            'operation-not-found': 'client-operation-not-found',
            'operation-ambiguous': 'client-operation-ambiguous',
            'document-invalid'   : 'client-document-invalid',
            'document-too-large' : 'client-document-too-large',
            'get-mutation'       : 'client-get-mutation',
            'retry-mutation'     : 'client-retry-mutation',
            'variable-missing'   : 'client-variable-missing',
            'variable-type'      : 'client-variable-type',
            'parameter-unbound'  : 'client-parameter-unbound',
            'scalar-conflict'    : 'client-scalar-conflict',
    ]

    private static final String CLIENT = '''package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;
import io.micronaut.retry.annotation.Retryable;

%s@GraphQLClient(schema = "%s")
public interface BookClient {

%s
}'''

    private static final String METHOD = '''    @GraphQLOperation(%s)
    String %s(%s);
'''

    private static final String BOOK = 'query Book($id: ID!) { book(id: $id) { title } }'


    private ClientCodeScenarios() {
    }

    /** @return the message of the one compile error of the scenario, with the temporary directory written as {@code <client directory>} */
    static String message(String scenario) {
        String schema = 'books'
        String schemaText = SCHEMA
        String operation = '{ version }'
        Map<String, String> files = [:]
        boolean checks = true
        String typeAnnotations = ''
        String arguments = null
        String parameters = ''
        String methods = null
        switch (scenario) {
            case 'schema-missing':
                schema = 'library'
                break
            case 'schema-invalid':
                schemaText = 'type Query {\n  book: Missing\n}\n'
                break
            case 'operation-not-found':
                operation = 'FindBook'
                break
            case 'operation-ambiguous':
                operation = 'query First { version } query Second { version }'
                break
            case 'document-invalid':
                operation = 'query {\\n  book(id: 1) {\\n    titel\\n  }\\n}'
                break
            case 'document-too-large':
                operation = 'Huge'
                checks = false
                files = ['books/huge.graphql': 'query Huge { ' + (0..<20000).collect { String.format('f%05d', it) }.join(' ') + ' }']
                break
            case 'get-mutation':
                operation = 'mutation Save($title: String!) { save(title: $title) { id } }'
                arguments = 'value = "' + operation + '", method = GraphQLOperation.Method.GET'
                parameters = 'String title'
                break
            case 'retry-mutation':
                operation = 'mutation Save($title: String!) { save(title: $title) { id } }'
                typeAnnotations = '@Retryable\n'
                parameters = 'String title'
                break
            case 'variable-missing':
                operation = BOOK
                break
            case 'variable-type':
                operation = BOOK
                parameters = 'int id'
                break
            case 'parameter-unbound':
                operation = BOOK
                parameters = 'String id, int limit'
                break
            case 'scalar-conflict':
                methods = String.format(METHOD, '"query At($moment: DateTime) { at(moment: $moment) }"', 'at', 'java.time.Instant moment') + '\n' +
                        String.format(METHOD, '"query Again($moment: DateTime) { at(moment: $moment) }"', 'atAgain', 'String moment')
                break
            default:
                throw new IllegalArgumentException('no scenario ' + scenario)
        }
        JavaParserCompilation compilation = new JavaParserCompilation().clientFile('books/schema.graphqls', schemaText)
                .source('example.BookClient', String.format(CLIENT, typeAnnotations, schema, methods ?: String.format(METHOD, arguments ?: '"' + operation + '"', 'book', parameters)))
        files.each { path, text -> compilation.clientFile(path, text) }
        if (!checks) {
            compilation.option(ProcessorOptions.CLIENT_CHECKS, 'false')
        }
        VisitorCompilation.Result result = compilation.compile()
        assert result.errors.size() == 1: result.errors
        String clientDirectory = result.schemaDir.parentFile.toPath().resolve('client').toString()
        return result.errors[0].replace(clientDirectory, '<client directory>').readLines()*.stripTrailing().join('\n')
    }
}
