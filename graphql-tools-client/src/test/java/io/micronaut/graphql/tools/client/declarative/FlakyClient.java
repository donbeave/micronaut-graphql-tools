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
import io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException;
import io.micronaut.graphql.tools.client.internal.GraphQLOperationMetadata;
import io.micronaut.retry.annotation.Retryable;

/**
 * A query that Micronaut Retry repeats, against an operation that answers 503 twice and then succeeds.
 */
@GraphQLClient(id = "decl", endpoint = "/echo/graphql")
public interface FlakyClient {

    String FLAKY = "query Flaky { book { title pages id } }";

    @Retryable(includes = GraphQLTransportException.class, attempts = "3", delay = "10ms")
    @GraphQLOperation(FLAKY)
    @GraphQLOperationMetadata(document = FLAKY, operationName = "Flaky", payload = "field")
    Book retried();

    @GraphQLOperation(FLAKY)
    @GraphQLOperationMetadata(document = FLAKY, operationName = "Flaky", payload = "field")
    Book once();
}
