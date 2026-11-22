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
package example.tck

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.cookie.Cookie
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import java.security.Principal
import java.util.Optional

// The fixtures of the TCK scenario `http-access`: resolvers that set a cookie and a header on the response, read a header of
// the request, and ask for the response in the plain and in the optional form.

// tag::http-access[]
@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "http-access")
class HttpAccessQueries {

    fun haSetCookie(response: MutableHttpResponse<*>): Boolean {
        response.cookie(Cookie.of("session", "abc").httpOnly(true).path("/"))
        response.header("X-Resolver", "set")
        return true
    }

    fun haRequestHeader(request: HttpRequest<*>): String? = request.headers.get("X-Tck")

    fun haResponse(response: MutableHttpResponse<*>): Boolean = true

    fun haOptionalResponse(response: Optional<MutableHttpResponse<*>>): Boolean = response.isPresent

    fun haRequestPath(request: HttpRequest<*>): String = request.path

    fun haRequestIdentity(request: HttpRequest<*>): Int = System.identityHashCode(request)

    fun haPrincipal(principal: Principal?): String = principal?.name ?: "none"

    fun haMutationRequestHeader(request: HttpRequest<*>): String? = request.headers.get("X-Tck")

    fun haMutationOptionalResponse(response: Optional<MutableHttpResponse<*>>): Boolean = response.isPresent

    fun haMutationResponse(response: MutableHttpResponse<*>): Boolean = true

    fun haSubscriptionRequestHeader(request: HttpRequest<*>): Publisher<String?> = Flux.just(request.headers.get("X-Tck"))

    fun haSubscriptionOptionalResponse(response: Optional<MutableHttpResponse<*>>): Publisher<Boolean> =
        Flux.just(response.isPresent)

    fun haSubscriptionResponse(response: MutableHttpResponse<*>): Publisher<Boolean> = Flux.just(true)
}
// end::http-access[]
