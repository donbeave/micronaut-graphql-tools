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

import io.micronaut.core.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The answer of a {@link GraphQLConnectionInterceptor}: accept the connection, with attributes that every operation of the
 * connection can read, or reject it, which closes the connection with a close code and a reason.
 */
public final class GraphQLConnectionResult {

    /** The close code of a rejection that names none: {@code 4403}, Forbidden, as the protocol defines it. */
    public static final int DEFAULT_REJECT_CODE = 4403;

    private static final int MAX_REASON_BYTES = 123;

    private static final GraphQLConnectionResult ACCEPTED = new GraphQLConnectionResult(true, Collections.emptyMap(), 0, "");

    private final boolean accepted;
    private final Map<String, Object> attributes;
    private final int closeCode;
    private final String reason;

    private GraphQLConnectionResult(boolean accepted, Map<String, Object> attributes, int closeCode, String reason) {
        this.accepted = accepted;
        this.attributes = attributes;
        this.closeCode = closeCode;
        this.reason = reason;
    }

    /**
     * @return a result that accepts the connection without attributes
     */
    @NonNull
    public static GraphQLConnectionResult accept() {
        return ACCEPTED;
    }

    /**
     * @param attributes the attributes of the connection, for example the user that the token identifies; the library
     *                   copies them, and every operation of the connection receives them in its execution
     * @return a result that accepts the connection
     */
    @NonNull
    public static GraphQLConnectionResult accept(@NonNull Map<String, Object> attributes) {
        Objects.requireNonNull(attributes, "attributes");
        return new GraphQLConnectionResult(true, Collections.unmodifiableMap(new LinkedHashMap<>(attributes)), 0, "");
    }

    /**
     * @return a result that closes the connection with {@link #DEFAULT_REJECT_CODE} and the reason {@code Forbidden}
     */
    @NonNull
    public static GraphQLConnectionResult reject() {
        return reject(DEFAULT_REJECT_CODE, "Forbidden");
    }

    /**
     * @param closeCode the WebSocket close code, in the range {@code 4000} to {@code 4999} that is free for applications
     * @param reason    the close reason that the client receives, at most 123 bytes in UTF-8; keep it short and free of internals
     * @return a result that closes the connection
     */
    @NonNull
    public static GraphQLConnectionResult reject(int closeCode, @NonNull String reason) {
        if (closeCode < 4000 || closeCode > 4999) {
            throw new IllegalArgumentException("The close code of a rejection is in 4000..4999, was " + closeCode);
        }
        Objects.requireNonNull(reason, "reason");
        if (reason.getBytes(StandardCharsets.UTF_8).length > MAX_REASON_BYTES) {
            throw new IllegalArgumentException("The reason of a rejection is at most " + MAX_REASON_BYTES
                    + " bytes in UTF-8, the limit of a WebSocket close frame (RFC 6455)");
        }
        return new GraphQLConnectionResult(false, Collections.emptyMap(), closeCode, reason);
    }

    /**
     * @return whether the connection is accepted
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * @return the attributes of an accepted connection; empty for a rejection
     */
    @NonNull
    public Map<String, Object> attributes() {
        return attributes;
    }

    /**
     * @return the close code of a rejection, {@code 0} for an accepted connection
     */
    public int closeCode() {
        return closeCode;
    }

    /**
     * @return the close reason of a rejection, empty for an accepted connection
     */
    @NonNull
    public String reason() {
        return reason;
    }

}
