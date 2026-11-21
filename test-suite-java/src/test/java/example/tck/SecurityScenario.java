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

import graphql.GraphQLContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.security.RootFieldsHook;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.AuthenticationFetcher;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.utils.SecurityService;
import graphql.language.OperationDefinition;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The fixtures of the TCK scenario {@code security}: an authentication from request headers, secured and unsecured resolvers, a
 * child field that runs after an async parent, and a hook that records the root fields.
 */
public final class SecurityScenario {

    private static final String SCENARIO = "security";

    private SecurityScenario() {
    }

    /** Authenticates a request that has {@code X-User}, with the comma separated roles of {@code X-Roles}. */
    @Singleton
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class HeaderAuthenticationFetcher implements AuthenticationFetcher<HttpRequest<?>> {

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

    // tag::root-fields-hook[]
    @Singleton
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class RecordingHook implements RootFieldsHook {

        @Override
        public void onRootFields(OperationDefinition.Operation operation, List<String> rootFields, GraphQLContext context) {
            context.put("tck.roots", String.join(",", rootFields));
        }
    }
    // end::root-fields-hook[]

    @GraphQLType
    public static class SecMe {
    }

    // tag::secured[]
    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class SecurityQueries {

        public String secOpen() {
            return "open";
        }

        @Secured(SecurityRule.IS_AUTHENTICATED)
        public String secAuthenticated() {
            return "authenticated";
        }

        @Secured("ROLE_ADMIN")
        public String secAdmin() {
            return "admin";
        }

        @Secured(SecurityRule.DENY_ALL)
        public String secNobody() {
            return "nobody";
        }

        public CompletionStage<SecMe> secMe() {
            return CompletableFuture.supplyAsync(SecMe::new);
        }

        public String secRoots(GraphQLContext context) {
            return context.get("tck.roots");
        }
    }
    // end::secured[]

    @Singleton
    @GraphQLTypeResolver(SecMe.class)
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class SecMeFields {

        private final SecurityService securityService;

        public SecMeFields(SecurityService securityService) {
            this.securityService = securityService;
        }

        public CompletionStage<String> principal(SecMe me, Authentication authentication) {
            return CompletableFuture.supplyAsync(() -> authentication == null ? null : authentication.getName());
        }

        public String service(SecMe me) {
            return securityService.getAuthentication().map(Authentication::getName).orElse(null);
        }
    }

}
