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

import io.micronaut.graphql.tools.benchmarks.Fixtures.ClientBook;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

/**
 * The types that the loopback clients send and receive.
 */
public final class LoopbackModels {

    private LoopbackModels() {
    }

    /** The data of the response, for the clients that do not unwrap the root field. */
    @Serdeable
    public record BookData(ClientBook book) {
    }

    /** The response as the plain HTTP client reads it. */
    @Serdeable
    public record RawResponse(BookData data) {
    }

    /** The request as the plain HTTP client sends it. */
    @Serdeable
    public record RawRequest(String query, String operationName, Map<String, Object> variables) {
    }
}
