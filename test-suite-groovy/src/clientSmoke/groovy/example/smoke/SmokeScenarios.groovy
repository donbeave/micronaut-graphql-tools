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
package example.smoke

import example.client.ExampleClient
import example.client.ExampleClient.AuthPayload
import example.client.ExampleClient.OperationError
import example.client.ExampleClient.Partial
import example.client.ExampleClient.Profile
import example.client.ExampleClient.ProfileName
import example.client.ExampleClient.SecurityError
import example.client.ExampleClient.SignUpInput
import example.client.ExampleClient.ValidationError
import example.client.ExampleClient.ValidationErrorCode
import example.client.ServiceClient
import groovy.transform.CompileStatic
import groovy.transform.Canonical
import io.micronaut.graphql.tools.client.GraphQLHttpClient
import io.micronaut.graphql.tools.client.GraphQLRequest
import io.micronaut.graphql.tools.client.GraphQLResponse
import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.exceptions.GraphQLErrorsException
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton

import java.time.Instant

/**
 * The scenarios of the native client smoke test: the query, mutation, union, error and scalar cases of the client tests, on the
 * declarative clients of the `serdeTest` source set and on the low-level client.
 */
@CompileStatic
@Singleton
class SmokeScenarios {

    @Serdeable
    @Canonical
    static class Found {
        String username
        String fullName
    }

    @Serdeable
    @Canonical
    static class Search {
        List<Found> searchProfile
    }

    private final ExampleClient client
    private final ServiceClient service
    private final GraphQLHttpClient lowLevel
    private int scenarios

    SmokeScenarios(ExampleClient client, ServiceClient service, @GraphQLClient('example') GraphQLHttpClient lowLevel) {
        this.client = client
        this.service = service
        this.lowLevel = lowLevel
    }

    int run() {
        payloadMethod()
        envelopeMethod()
        mutationWithInput()
        unionResult()
        fragmentOperation()
        scalarRoundTrip()
        partialDataWithError()
        serviceClientParameters()
        typedLowLevelQuery()
        dynamicLowLevelQuery()
        scenarios
    }

    private void payloadMethod() {
        scenario('a payload method returns the root field')
        expect('a payload method', client.profiles('bob', null), [new Profile('bob', 'Bob Di')])
    }

    private void envelopeMethod() {
        scenario('an envelope method returns the response')
        GraphQLResponse<List<ExampleClient.Book>> response = client.allBooks()
        expect('an envelope method: errors', response.errors().empty, true)
        expect('an envelope method: books', response.data().size(), 5)
        expect('an envelope method: status', response.httpResponse().code(), 200)
        expect('an envelope method: first book', response.data().first().id, 'ab')
    }

    private void mutationWithInput() {
        scenario('a mutation takes an input type')
        GraphQLResponse<AuthPayload> response = client.signUp(new SignUpInput('mary', 'secret', 'Mary', 'Jones'))
        expect('a mutation: user', response.data().data.username, 'mary')
        expect('a mutation: error', response.data().error, null)
    }

    private void unionResult() {
        scenario('a union is read into a sealed result')
        OperationError missing = client.signIn('unknown', 'x').data().error
        expect('a union: member', missing instanceof ValidationError, true)
        expect('a union: message', ((ValidationError) missing).message, 'User unknown not found.')
        expect('a union: code', ((ValidationError) missing).validationCode, ValidationErrorCode.NOT_FOUND)
        OperationError wrong = client.signIn('test', 'xyz').data().error
        expect('a union: other member', wrong instanceof SecurityError, true)
        expect('a union: other message', ((SecurityError) wrong).message, 'Password is incorrect.')
    }

    private void fragmentOperation() {
        scenario('a fragment of the client directory is part of the operation')
        expect('a fragment of the client directory', client.profileNames('bob'), [new ProfileName('bob')])
    }

    private void scalarRoundTrip() {
        scenario('java.time and Long round trip')
        expect('an Instant', client.echoInstant(Instant.parse('2026-12-24T10:15:30Z')), Instant.parse('2026-12-24T10:16:30Z'))
        expect('a Long beyond the double range', client.echoLong(9007199254740993L), 9007199254740993L)
    }

    private void partialDataWithError() {
        scenario('an error keeps the partial data')
        GraphQLResponse<Partial> response = client.partial()
        expect('partial data: errors', response.errors().empty, false)
        expect('partial data: path', response.errors().first().path(), ['unreliable'] as List<Object>)
        expect('partial data: introduced', response.data().introduced, 'introduced introduced')
        expect('partial data: null field', response.data().unreliable, null)
        GraphQLErrorsException failure = null
        try {
            response.dataOrThrow()
        } catch (GraphQLErrorsException expected) {
            failure = expected
        }
        expect('partial data: exception', failure != null, true)
        expect('partial data: exception data', ((Partial) failure.response.data()).introduced, 'introduced introduced')
    }

    private void serviceClientParameters() {
        scenario('header and cookie parameters of a service client')
        expect('a header parameter', service.viewer('bob'), new ServiceClient.Viewer('bob'))
        expect('a header parameter left out', service.viewer(null), null)
        expect('a cookie parameter', service.viewerOfSession('abc'), null)
    }

    private void typedLowLevelQuery() {
        scenario('a typed query decodes the data')
        GraphQLRequest<Search> request = GraphQLRequest.builder(Search)
                .document('query Find($contains: String!) { searchProfile(contains: $contains) { username fullName } }')
                .operationName('Find')
                .variable('contains', 'bob')
                .build()
        GraphQLResponse<Search> response = lowLevel.executeBlocking(request)
        expect('a typed query', response.dataOrThrow().searchProfile, [new Found('bob', 'Bob Di')])
    }

    private void dynamicLowLevelQuery() {
        scenario('a dynamic query decodes a map')
        GraphQLResponse<Map<String, Object>> response = lowLevel.executeBlocking(
                GraphQLRequest.builder().document('{ searchProfile(contains: "bob") { username } }').build())
        Map<String, Object> data = response.dataOrThrow()
        List<Object> found = (List<Object>) data.get('searchProfile')
        expect('a dynamic query', ((Map<String, Object>) found.first()).get('username'), 'bob')
    }

    private void scenario(String name) {
        scenarios++
        System.out.println("scenario " + scenarios + ": " + name)
    }

    private void expect(String scenario, Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) {
            throw new IllegalStateException("${scenario}: expected <${expected}> but was <${actual}>")
        }
    }
}
