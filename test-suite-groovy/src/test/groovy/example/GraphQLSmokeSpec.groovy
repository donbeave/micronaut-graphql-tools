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
package example

import example.graphql.type.SecurityErrorCode
import example.graphql.type.ValidationErrorCode
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLSchema
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Stepwise

@MicronautTest
@Stepwise
class GraphQLSmokeSpec extends Specification {

    @Inject
    @Client('/')
    HttpClient client

    @Inject
    GraphQLSchema schema

    private Map<String, Object> execute(String query, Map<String, Object> variables = [:]) {
        Map<String, Object> response = client.toBlocking().retrieve(
                HttpRequest.POST('/graphql', [query: query, variables: variables]),
                Argument.mapOf(Argument.STRING, Argument.OBJECT_ARGUMENT))
        assert response.errors == null: "GraphQL errors: ${response.errors}"
        (Map<String, Object>) response.data
    }

    def 'addBook requires sign in'() {
        when:
        Map payload = execute('mutation($id: ID!) { addBook(id: $id) { error { __typename ... on SecurityError { securityCode: code message } } } }',
                [id: 'ab']).addBook

        then:
        payload.error.__typename == 'SecurityError'
        payload.error.securityCode == 'NOT_AUTHENTICATED'
    }

    def 'an introduction advice resolver binds its abstract method'() {
        expect:
        execute('{ introduced }').introduced == 'introduced introduced'
    }

    def 'allBooks resolves type resolvers and the data loader'() {
        when:
        List<Map> books = execute('{ allBooks { id title year label sellers { name price } } }').allBooks

        then:
        books.size() == 5
        books.first().id == 'ab'
        books.first().title == 'The Fractured Sage'
        books.first().year == 1982
        books.first().label == 'The Fractured Sage (1982)'
        books.first().sellers*.name == ['Amaz', 'Yah']
        books.first().sellers*.price*.doubleValue() == [20.0d, 21.0d]
    }

    def 'searchProfile with a limit'() {
        when:
        List<Map> profiles = execute('query($contains: String!, $limit: Int) { searchProfile(contains: $contains, limit: $limit) { username fullName } }',
                [contains: 'T', limit: 1]).searchProfile

        then:
        profiles.size() == 1
        profiles.first().username == 'test'
        profiles.first().fullName == 'Test Test'
    }

    def 'signIn failures'() {
        given:
        String query = 'mutation($u: String!, $p: String!) { signIn(username: $u, password: $p) { data { username } error { __typename ... on ValidationError { validationCode: code message } ... on SecurityError { securityCode: code message } } } }'

        when:
        Map unknown = execute(query, [u: 'unknown-user', p: 'test']).signIn
        Map wrong = execute(query, [u: 'test', p: 'xyz']).signIn

        then:
        unknown.data == null
        unknown.error.validationCode == 'NOT_FOUND'
        unknown.error.message == 'User unknown-user not found.'
        wrong.data == null
        wrong.error.__typename == 'SecurityError'
        wrong.error.securityCode == 'INCORRECT_CREDENTIALS'
    }

    def 'signUp then duplicate'() {
        given:
        String query = 'mutation($input: SignUpInput!) { signUp(input: $input) { data { username firstName lastName books { id } } error { __typename ... on ValidationError { validationCode: code message } } } }'
        Map input = [username: 'john', password: 'test', firstName: 'John', lastName: 'Johnathan']

        when:
        Map created = execute(query, [input: input]).signUp
        Map duplicate = execute(query, [input: input]).signUp

        then:
        created.error == null
        created.data.username == 'john'
        created.data.books == []
        duplicate.data == null
        duplicate.error.validationCode == 'DUPLICATE'
        duplicate.error.message == 'User with username john already exists.'
    }

    def 'signIn then addBook'() {
        when:
        Map signIn = execute('mutation { signIn(username: "bob", password: "bodi") { data { username } } }').signIn
        Map addBook = execute('mutation { addBook(id: "ax") { error { __typename } } }').addBook
        Map current = execute('{ currentUser { username books { id label } } }').currentUser

        then:
        signIn.data.username == 'bob'
        addBook.error == null
        current.username == 'bob'
        current.books*.id == ['ax']
        current.books.first().label != null
    }

    def 'schema enum values are the Groovy constants'() {
        expect:
        ((GraphQLEnumType) schema.getType('SecurityErrorCode')).getValue('NOT_AUTHENTICATED').value.is(SecurityErrorCode.NOT_AUTHENTICATED)
        ((GraphQLEnumType) schema.getType('ValidationErrorCode')).getValue('DUPLICATE').value.is(ValidationErrorCode.DUPLICATE)
    }
}
