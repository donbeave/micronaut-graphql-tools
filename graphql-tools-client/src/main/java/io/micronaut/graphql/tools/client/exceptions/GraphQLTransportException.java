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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.simple.SimpleHttpHeaders;

/**
 * The call failed below GraphQL: a connection error, a timeout, or an HTTP response that is not a GraphQL response.
 *
 * @since 1.0.0
 */
public class GraphQLTransportException extends GraphQLClientException {

    /** The code of a failure that is not a timeout. */
    public static final String CODE = "transport-error";

    /** The code of a timeout. */
    public static final String TIMEOUT_CODE = "transport-timeout";

    /** The value of {@link #getStatus()} when no HTTP response was received. */
    public static final int NO_STATUS = -1;

    private static final long serialVersionUID = 1L;

    private final int status;
    private final transient HttpHeaders headers;
    private final boolean timeout;

    /**
     * Creates an exception.
     *
     * @param message the message, without the code
     * @param status  the HTTP status, or {@link #NO_STATUS}
     * @param headers the headers of the HTTP response, or {@code null} when there is none
     * @param timeout whether the call timed out
     * @param cause   the cause, or {@code null}
     */
    public GraphQLTransportException(String message, int status, @Nullable HttpHeaders headers, boolean timeout, @Nullable Throwable cause) {
        super(timeout ? TIMEOUT_CODE : CODE, message, cause);
        this.status = status;
        this.headers = headers == null ? new SimpleHttpHeaders(ConversionService.SHARED) : headers;
        this.timeout = timeout;
    }

    /**
     * Returns the HTTP status.
     *
     * @return the status code, or {@link #NO_STATUS} when no HTTP response was received
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the headers of the HTTP response.
     *
     * @return the headers, empty when no HTTP response was received
     */
    public HttpHeaders getHeaders() {
        return headers;
    }

    /**
     * Tells whether the call timed out.
     *
     * @return {@code true} for a timeout
     */
    public boolean isTimeout() {
        return timeout;
    }
}
