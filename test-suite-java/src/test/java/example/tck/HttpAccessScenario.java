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
package example.tck;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.security.Principal;
import java.util.Optional;

/**
 * The fixtures of the TCK scenario {@code http-access}: resolvers that set a cookie and a header on the response, read a header
 * of the request, and ask for the response in the plain and in the optional form, and read the request path and the principal; the same on a
 * mutation and on a subscription.
 */
public final class HttpAccessScenario {

    private static final String SCENARIO = "http-access";

    private HttpAccessScenario() {
    }

    // tag::http-access[]
    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class HttpAccessQueries {

        public boolean haSetCookie(MutableHttpResponse<?> response) {
            response.cookie(Cookie.of("session", "abc").httpOnly(true).path("/"));
            response.header("X-Resolver", "set");
            return true;
        }

        public String haRequestHeader(HttpRequest<?> request) {
            return request.getHeaders().get("X-Tck");
        }

        public Boolean haResponse(MutableHttpResponse<?> response) {
            return response != null;
        }

        public boolean haOptionalResponse(Optional<MutableHttpResponse<?>> response) {
            return response.isPresent();
        }

        public String haRequestPath(HttpRequest<?> request) {
            return request.getPath();
        }

        public int haRequestIdentity(HttpRequest<?> request) {
            return System.identityHashCode(request);
        }

        public String haPrincipal(Principal principal) {
            return principal == null ? "none" : principal.getName();
        }

        public String haMutationRequestHeader(HttpRequest<?> request) {
            return request.getHeaders().get("X-Tck");
        }

        public boolean haMutationOptionalResponse(Optional<MutableHttpResponse<?>> response) {
            return response.isPresent();
        }

        public Boolean haMutationResponse(MutableHttpResponse<?> response) {
            return response != null;
        }

        public Publisher<String> haSubscriptionRequestHeader(HttpRequest<?> request) {
            return Flux.just(request.getHeaders().get("X-Tck"));
        }

        public Publisher<Boolean> haSubscriptionOptionalResponse(Optional<MutableHttpResponse<?>> response) {
            return Flux.just(response.isPresent());
        }

        public Publisher<Boolean> haSubscriptionResponse(MutableHttpResponse<?> response) {
            return Flux.just(response != null);
        }
    }
    // end::http-access[]

}
