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

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation
import io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Header
import io.micronaut.retry.annotation.Retryable
import io.micronaut.serde.annotation.Serdeable

/**
 * A declarative client that finds the server by its service id: the address comes from
 * `micronaut.http.services.example.urls`, so a client filter of that service id and the HTTP client settings of the service apply.
 */
@GraphQLClient(id = "example", schema = "server")
interface ServiceClient {

    @Serdeable
    data class Viewer(val username: String)

    // tag::headers[]
    @GraphQLOperation("query Viewer { currentUser { username } }")
    suspend fun viewer(@Header("X-User") user: String?): Viewer?
    // end::headers[]

    // tag::cookie[]
    @GraphQLOperation("query Viewer { currentUser { username } }")
    suspend fun viewerOfSession(@CookieValue("session") session: String): Viewer?
    // end::cookie[]

    // tag::retry[]
    @Retryable(includes = [GraphQLTransportException::class], attempts = "3", delay = "10ms")
    @GraphQLOperation("query Profiles(\$contains: String!) { searchProfile(contains: \$contains) { username } }")
    suspend fun profiles(contains: String): List<Viewer>
    // end::retry[]

    @GraphQLOperation("query Profiles(\$contains: String!) { searchProfile(contains: \$contains) { username } }")
    suspend fun profilesOnce(contains: String): List<Viewer>
}
