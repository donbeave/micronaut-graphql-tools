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
package io.micronaut.graphql.tools.benchmarks.loopback;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.benchmarks.Fixtures.ClientBook;
import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;
import io.micronaut.graphql.tools.client.internal.GraphQLOperationMetadata;

/**
 * The declarative client of the loopback cases, with the operation metadata written by hand as the code generator writes it.
 */
@Requires(property = LoopbackCases.ENABLED, value = "true")
@GraphQLClient(id = LoopbackCases.SERVICE, endpoint = "/loopback/graphql")
public interface DeclarativeLoopClient {

    @GraphQLOperation(LoopbackCases.DOCUMENT)
    @GraphQLOperationMetadata(document = LoopbackCases.DOCUMENT, operationName = "Book", variables = "id", payload = "field")
    ClientBook book(String id);
}
