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
package io.micronaut.graphql.tools.websocket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphQLConnectionResultTest {

    @Test
    void reasonAtTheCloseFrameLimitIsAccepted() {
        assertEquals(123, GraphQLConnectionResult.reject(4401, "a".repeat(123)).reason().length());
    }

    @Test
    void reasonOverTheCloseFrameLimitInUtf8BytesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> GraphQLConnectionResult.reject(4401, "a".repeat(124)));
        assertThrows(IllegalArgumentException.class, () -> GraphQLConnectionResult.reject(4401, "é".repeat(62)));
    }
}
