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
package io.micronaut.graphql.tools.binding.internal.client;

import io.micronaut.core.annotation.Internal;

/**
 * What the payload of an operation is: the value of its only root field, or the whole {@code data} object when the operation
 * selects several root fields.
 */
@Internal
public enum PayloadMode {

    /** The operation selects one root field; the payload is the value of that field. */
    FIELD("field"),

    /** The operation selects several root fields; the payload is the {@code data} object. */
    DATA("data");

    private final String token;

    PayloadMode(String token) {
        this.token = token;
    }

    /**
     * @return the word that the metadata of the operation carries
     */
    public String token() {
        return token;
    }
}
