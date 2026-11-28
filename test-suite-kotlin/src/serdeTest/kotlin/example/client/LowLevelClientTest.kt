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

import io.micronaut.graphql.tools.client.GraphQLHttpClient
import io.micronaut.graphql.tools.client.GraphQLRequest
import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest
class LowLevelClientTest(@GraphQLClient("/") private val client: GraphQLHttpClient) {

    @Serdeable
    data class Profile(val username: String, val fullName: String)

    @Serdeable
    data class Search(val searchProfile: List<Profile>)

    @Test
    fun aTypedQueryDecodesTheData() {
        // tag::typed-query[]
        val request = GraphQLRequest.builder(Search::class.java)
            .document("query Find(\$contains: String!) { searchProfile(contains: \$contains) { username fullName } }")
            .operationName("Find")
            .variable("contains", "bob")
            .build()

        val response = client.executeBlocking(request)

        assertEquals(listOf(Profile("bob", "Bob Di")), response.dataOrThrow().searchProfile)
        // end::typed-query[]
        assertEquals(200, response.httpResponse().code())
    }

    @Test
    fun aDynamicQueryDecodesAMap() {
        // tag::dynamic-query[]
        val response = client.executeBlocking(
            GraphQLRequest.builder().document("{ searchProfile(contains: \"bob\") { username } }").build()
        )

        assertEquals(mapOf("searchProfile" to listOf(mapOf("username" to "bob"))), response.dataOrThrow())
        // end::dynamic-query[]
    }
}
