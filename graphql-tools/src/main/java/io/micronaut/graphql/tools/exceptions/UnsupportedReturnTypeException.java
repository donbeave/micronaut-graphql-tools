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
package io.micronaut.graphql.tools.exceptions;

import io.micronaut.graphql.tools.MappingContext;

/**
 * Thrown when the declared return type of a field wraps the mapped class in a way the library cannot bind, for
 * example a {@code DataFetcherResult} inside a {@code DataFetcherResult}, or an {@code Optional} on a non-null GraphQL
 * type.
 */
public final class UnsupportedReturnTypeException extends AbstractMappingException {

    private final String reason;

    public UnsupportedReturnTypeException(MappingContext mappingContext, String reason) {
        super("The return type of the method is not supported for this field: " + reason, mappingContext);

        this.reason = reason;
    }

    /**
     * @return why the return type is not supported
     */
    public String getReason() {
        return reason;
    }

}
