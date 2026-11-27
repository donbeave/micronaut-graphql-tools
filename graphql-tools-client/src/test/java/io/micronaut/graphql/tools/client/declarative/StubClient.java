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

import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;
import io.micronaut.graphql.tools.client.internal.GraphQLOperationMetadata;
import io.micronaut.http.annotation.Header;

/**
 * A client of the stub server of the HTTP client specs, which answers with the status and the body that the {@code X-Status} and
 * {@code X-Kind} headers name, at the default endpoint.
 */
@GraphQLClient("decl")
public interface StubClient {

    @GraphQLOperation("query A { a }")
    @GraphQLOperationMetadata(document = "query A { a }", operationName = "A", payload = "field")
    Integer data();

    @Header(name = "X-Kind", value = "partial")
    @GraphQLOperation("query A { a }")
    @GraphQLOperationMetadata(document = "query A { a }", operationName = "A", payload = "field")
    Integer partial();

    @Header(name = "X-Kind", value = "partial")
    @GraphQLOperation("query A { a }")
    @GraphQLOperationMetadata(document = "query A { a }", operationName = "A", payload = "field")
    GraphQLResponse<Integer> partialResponse();

    @Header(name = "X-Kind", value = "null-data")
    @GraphQLOperation("query A { a }")
    @GraphQLOperationMetadata(document = "query A { a }", operationName = "A", payload = "data")
    GraphQLResponse<Integer> nullData();

    @Header(name = "X-Kind", value = "errors")
    @Header(name = "X-Status", value = "400")
    @GraphQLOperation("query A { a }")
    @GraphQLOperationMetadata(document = "query A { a }", operationName = "A", payload = "field")
    GraphQLResponse<Integer> requestError();

    @Header(name = "X-Kind", value = "html")
    @Header(name = "X-Status", value = "502")
    @GraphQLOperation("query A { a }")
    @GraphQLOperationMetadata(document = "query A { a }", operationName = "A", payload = "field")
    Integer gatewayError();

    @Header(name = "X-Kind", value = "wrong-shape")
    @GraphQLOperation("query A { a }")
    @GraphQLOperationMetadata(document = "query A { a }", operationName = "A", payload = "field")
    Integer wrongShape();
}
