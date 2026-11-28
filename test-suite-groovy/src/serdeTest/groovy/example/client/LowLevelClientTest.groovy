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

import groovy.transform.Canonical
import io.micronaut.graphql.tools.client.GraphQLHttpClient
import io.micronaut.graphql.tools.client.GraphQLRequest
import io.micronaut.graphql.tools.client.GraphQLResponse
import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class LowLevelClientTest extends Specification {

    @Serdeable
    @Canonical
    static class Profile {
        String username
        String fullName
    }

    @Serdeable
    @Canonical
    static class Search {
        List<Profile> searchProfile
    }

    @Inject
    @GraphQLClient('/')
    GraphQLHttpClient client

    def 'a typed query decodes the data'() {
        expect:
        // tag::typed-query[]
        GraphQLRequest<Search> request = GraphQLRequest.builder(Search)
                .document('query Find($contains: String!) { searchProfile(contains: $contains) { username fullName } }')
                .operationName('Find')
                .variable('contains', 'bob')
                .build()

        GraphQLResponse<Search> response = client.executeBlocking(request)

        response.dataOrThrow().searchProfile == [new Profile('bob', 'Bob Di')]
        // end::typed-query[]
        response.httpResponse().code() == 200
    }

    def 'a dynamic query decodes a map'() {
        expect:
        // tag::dynamic-query[]
        GraphQLResponse<Map<String, Object>> response = client.executeBlocking(
                GraphQLRequest.builder().document('{ searchProfile(contains: "bob") { username } }').build())

        response.dataOrThrow() == [searchProfile: [[username: 'bob']]]
        // end::dynamic-query[]
    }
}
