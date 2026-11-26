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
package io.micronaut.graphql.tools.client.annotation;

import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.graphql.tools.client.internal.GraphQLOperationMetadata;

public final class ClientFixtures {

    private ClientFixtures() {
    }

    public static class CustomConfiguration extends HttpClientConfiguration {
        @Override
        public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
            return null;
        }
    }

    @GraphQLClient("books")
    public interface ValueClient {

        @GraphQLOperation("query { books { id } }")
        String books();
    }

    @GraphQLClient(id = "authors", endpoint = "/api/graphql", webSocketEndpoint = "/api/ws", schema = "library",
            configuration = CustomConfiguration.class)
    public interface FullClient {

        @GraphQLOperation(value = "query($id: ID!) { author(id: $id) { name } }", name = "author", method = GraphQLOperation.Method.GET)
        String author(@GraphQLVariable("id") String authorId);

        @GraphQLOperation
        String plain(String id);

        @GraphQLOperation("mutation { touch }")
        @GraphQLOperationMetadata(document = "mutation Touch { touch }", operationName = "Touch", operationType = "mutation")
        String touch();
    }

    @GraphQLClient
    public interface BareClient {
    }

    public static class InjectionPoints {

        @GraphQLClient("inline")
        public Object field;

        public void accept(@GraphQLClient("param") Object client) {
        }
    }
}
