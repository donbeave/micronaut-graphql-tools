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
package example.graphql;

import example.data.AuthenticationService;
import graphql.GraphQLContext;
import io.micronaut.graphql.tools.execution.GraphQLContextContributor;
import io.micronaut.graphql.tools.execution.GraphQLExecutionRequest;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;

/**
 * Puts the user of each request in the GraphQL context of its execution, from the header {@value #HEADER}, which stands in
 * for a real credential such as a token. The state belongs to the execution, so concurrent requests never see each
 * other's user, and the resolvers read it from the {@code DataFetchingEnvironment}.
 */
@Singleton
public class CurrentUserContributor implements GraphQLContextContributor {

    public static final String HEADER = "X-User";

    private final AuthenticationService authenticationService;

    public CurrentUserContributor(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    public void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
        HttpRequest<?> http = request.getHttpRequest();
        if (http == null) {
            return;
        }
        http.getHeaders().get(HEADER, String.class)
                .flatMap(authenticationService::find)
                .ifPresent(user -> context.put(AuthenticationService.CURRENT_USER, user));
    }
}
