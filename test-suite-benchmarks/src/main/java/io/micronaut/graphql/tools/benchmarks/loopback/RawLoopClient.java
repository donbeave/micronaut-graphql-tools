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
import io.micronaut.graphql.tools.benchmarks.loopback.LoopbackModels.RawRequest;
import io.micronaut.graphql.tools.benchmarks.loopback.LoopbackModels.RawResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;

/**
 * A plain declarative HTTP client with a POST that takes the request and returns the response envelope: what an application writes
 * without the GraphQL client.
 */
@Requires(property = LoopbackCases.ENABLED, value = "true")
@Client(id = LoopbackCases.SERVICE, path = "/loopback")
public interface RawLoopClient {

    @Post("/graphql")
    RawResponse post(@Body RawRequest request);
}
