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
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLContextValue;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.execution.GraphQLContextContributor;
import io.micronaut.graphql.tools.execution.GraphQLExecutionRequest;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import jakarta.inject.Singleton;

import java.security.Principal;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The fixtures of the TCK scenario {@code resolver-parameters}: a contributor that puts a value in the context, a filter
 * that authenticates the request, and a resolver with one parameter of each injectable kind.
 */
public final class ResolverParametersScenario {

    private static final String SCENARIO = "resolver-parameters";

    private ResolverParametersScenario() {
    }

    @GraphQLType
    public static class ParamBook {

        private final String title;
        private final String author;

        public ParamBook(String title, String author) {
            this.title = title;
            this.author = author;
        }

        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }
    }

    @Singleton
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class TenantContributor implements GraphQLContextContributor {

        @Override
        public void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
            context.put("tck.tenant", "acme");
        }
    }

    @ServerFilter(Filter.MATCH_ALL_PATTERN)
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class AuthenticatingFilter {

        @RequestFilter
        public void authenticate(MutableHttpRequest<?> request) {
            request.setUserPrincipal(() -> "alice");
        }
    }

    // tag::parameters[]
    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class ParameterQueries {

        public String paramContext(GraphQLContext context) {
            return context.get("tck.tenant");
        }

        public String paramContextValue(@GraphQLContextValue("tck.tenant") String tenant) {
            return tenant;
        }

        public String paramMissingValue(@GraphQLContextValue("tck.absent") String absent) {
            return absent;
        }

        public String paramEnvironment(DataFetchingEnvironment environment) {
            return "Query." + environment.getFieldDefinition().getName();
        }

        public ParamBook paramSelection(DataFetchingFieldSelectionSet selection) {
            String selected = selection.getFields().stream().map(SelectedField::getName).collect(Collectors.joining(","));
            return new ParamBook(selected, "a");
        }

        public boolean paramLocale(Locale locale) {
            return locale != null;
        }

        public String paramRequest(HttpRequest<?> request) {
            return request.getPath();
        }

        public boolean paramResponse(MutableHttpResponse<?> response) {
            return response != null;
        }

        public boolean paramOptionalResponse(Optional<MutableHttpResponse<?>> response) {
            return response.isPresent();
        }

        public String paramPrincipal(Principal principal) {
            return principal.getName();
        }

        public String paramMixed(Locale locale, String name, @GraphQLContextValue("tck.tenant") String tenant,
                                 DataFetchingEnvironment environment, int count) {
            return name + ":" + count + ":" + tenant + ":Query." + environment.getFieldDefinition().getName();
        }
    }
    // end::parameters[]

}
