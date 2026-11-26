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
package example.federation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.AuthenticationFetcher;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.Arrays;
import java.util.List;

/** Authenticates a request that has {@code X-User}, with the comma separated roles of {@code X-Roles}. */
@Singleton
@Requires(env = FederationFixtures.FEDERATION)
public class FederationAuthentication implements AuthenticationFetcher<HttpRequest<?>> {

    @Override
    public Publisher<Authentication> fetchAuthentication(HttpRequest<?> request) {
        String user = request.getHeaders().get("X-User");
        if (user == null) {
            return Publishers.empty();
        }
        String roles = request.getHeaders().get("X-Roles");
        return Publishers.just(Authentication.build(user, roles == null ? List.of() : Arrays.asList(roles.split(","))));
    }
}
