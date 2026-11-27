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
package io.micronaut.graphql.tools.client.exceptions;

import io.micronaut.core.annotation.Nullable;

/**
 * The base of every failure of a client call. {@link #getCode()} is a lower-case kebab-case word that never changes after a
 * release, and the message starts with it in square brackets.
 *
 * @since 1.0.0
 */
public class GraphQLClientException extends RuntimeException {

    /** The code of a response that has neither data nor errors. */
    public static final String NO_DATA = "no-data";

    /** The code of a response body that cannot be decoded. */
    public static final String DECODE_ERROR = "decode-error";

    private static final long serialVersionUID = 1L;

    private final String code;

    /**
     * Creates an exception.
     *
     * @param code    the code
     * @param message the message, without the code
     */
    public GraphQLClientException(String code, String message) {
        this(code, message, null);
    }

    /**
     * Creates an exception.
     *
     * @param code    the code
     * @param message the message, without the code
     * @param cause   the cause, or {@code null}
     */
    public GraphQLClientException(String code, String message, @Nullable Throwable cause) {
        super('[' + code + "] " + message, cause);
        this.code = code;
    }

    /**
     * Returns the code of the failure.
     *
     * @return the code
     */
    public String getCode() {
        return code;
    }
}
