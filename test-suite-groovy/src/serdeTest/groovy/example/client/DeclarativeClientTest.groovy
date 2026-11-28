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
package example.client

import example.client.ExampleClient.AuthPayload
import example.client.ExampleClient.OperationError
import example.client.ExampleClient.OtherError
import example.client.ExampleClient.Partial
import example.client.ExampleClient.Profile
import example.client.ExampleClient.ProfileName
import example.client.ExampleClient.SecurityError
import example.client.ExampleClient.SignUpInput
import example.client.ExampleClient.ValidationError
import example.client.ExampleClient.ValidationErrorCode
import io.micronaut.graphql.tools.client.GraphQLResponse
import io.micronaut.graphql.tools.client.exceptions.GraphQLErrorsException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.Instant

@MicronautTest
class DeclarativeClientTest extends Specification {

    @Inject
    ExampleClient client

    def 'a payload method returns the root field'() {
        expect:
        // tag::payload[]
        List<Profile> profiles = client.profiles('bob', null)

        profiles == [new Profile('bob', 'Bob Di')]
        // end::payload[]
    }

    def 'an envelope method returns the response'() {
        expect:
        // tag::envelope[]
        GraphQLResponse<List<ExampleClient.Book>> response = client.allBooks()

        response.errors().empty
        response.data().size() == 5
        response.httpResponse().code() == 200
        // end::envelope[]
    }

    def 'a mutation takes an input type'() {
        expect:
        // tag::mutation[]
        GraphQLResponse<AuthPayload> response = client.signUp(new SignUpInput('mary', 'secret', 'Mary', 'Jones'))

        response.data().data.username == 'mary'
        // end::mutation[]
        response.data().error == null
    }

    def 'a union is read into a sealed result'() {
        expect:
        // tag::union[]
        OperationError error = client.signIn('unknown', 'x').data().error

        String message
        switch (error) {
            case ValidationError:
                message = ((ValidationError) error).message
                break
            case SecurityError:
                message = ((SecurityError) error).message
                break
            case OtherError:
                message = 'unknown'
                break
        }

        message == 'User unknown not found.'
        // end::union[]
        ((ValidationError) error).validationCode == ValidationErrorCode.NOT_FOUND
    }

    def 'a fragment of the client directory is part of the operation'() {
        expect:
        // tag::fragment[]
        List<ProfileName> names = client.profileNames('bob')

        names == [new ProfileName('bob')]
        // end::fragment[]
    }

    def 'java.time and Long round trip'() {
        expect:
        // tag::scalars[]
        client.echoInstant(Instant.parse('2026-12-24T10:15:30Z')) == Instant.parse('2026-12-24T10:16:30Z')
        client.echoLong(9007199254740993L) == 9007199254740993L
        // end::scalars[]
    }

    def 'an error keeps the partial data'() {
        when:
        // tag::partial-data[]
        GraphQLResponse<Partial> response = client.partial()

        then:
        !response.errors().empty
        response.errors().first().path() == ['unreliable']
        response.data().introduced == 'introduced introduced'
        response.data().unreliable == null

        when:
        response.dataOrThrow()

        then:
        GraphQLErrorsException failure = thrown()
        ((Partial) failure.response.data()).introduced == 'introduced introduced'
        // end::partial-data[]
    }
}
