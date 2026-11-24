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
package io.micronaut.graphql.tools.testing.internal;

import graphql.GraphQLContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.execution.GraphQLContextContributor;
import io.micronaut.graphql.tools.execution.GraphQLExecutionRequest;
import io.micronaut.graphql.tools.testing.GraphQLTester;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Copies the context values of a {@link GraphQLTester} request, which travel as an attribute of its synthetic HTTP request, into
 * the context of the execution. A request that does not come from the tester is left alone.
 */
@Internal
@Singleton
public final class TestContextContributor implements GraphQLContextContributor {

    @Override
    @SuppressWarnings("unchecked")
    public void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
        HttpRequest<?> http = request.getHttpRequest();
        if (http == null) {
            return;
        }
        http.getAttribute(GraphQLTester.CONTEXT_ATTRIBUTE, Map.class).ifPresent(values -> context.putAll((Map<Object, Object>) values));
    }

}
