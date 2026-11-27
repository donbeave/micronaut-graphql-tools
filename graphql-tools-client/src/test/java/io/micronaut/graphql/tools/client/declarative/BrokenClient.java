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
package io.micronaut.graphql.tools.client.declarative;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;
import io.micronaut.graphql.tools.client.declarative.Types.Book;
import io.micronaut.graphql.tools.client.internal.GraphQLOperationMetadata;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.QueryValue;

/**
 * Operation methods that the declarative client cannot run, each one for its own reason.
 */
@GraphQLClient(id = "decl", endpoint = "/echo/graphql")
public interface BrokenClient {

    @GraphQLOperation("query NoMetadata { book { title } }")
    Book notCompiled();

    @GraphQLOperation("query Q($id: ID!) { book(id: $id) { title } }")
    @GraphQLOperationMetadata(document = "query Q($id: ID!) { book(id: $id) { title } }", operationName = "Q", variables = "id", payload = "field")
    Book bodyParameter(@Body String id);

    @GraphQLOperation("query Q($id: ID!) { book(id: $id) { title } }")
    @GraphQLOperationMetadata(document = "query Q($id: ID!) { book(id: $id) { title } }", operationName = "Q", variables = "id", payload = "field")
    Book queryParameter(@QueryValue String id);

    @GraphQLOperation("query Q($id: ID!) { book(id: $id) { title } }")
    @GraphQLOperationMetadata(document = "query Q($id: ID!) { book(id: $id) { title } }", operationName = "Q", variables = "id", payload = "field")
    Book undeclaredVariable(String id, String other);

    @GraphQLOperation(value = "mutation M { touch }", method = GraphQLOperation.Method.GET)
    @GraphQLOperationMetadata(document = "mutation M { touch }", operationName = "M", operationType = "mutation", payload = "field")
    boolean mutationOverGet();

    @GraphQLOperation("subscription S { tick }")
    @GraphQLOperationMetadata(document = "subscription S { tick }", operationName = "S", operationType = "subscription", payload = "field")
    Integer subscription();
}
