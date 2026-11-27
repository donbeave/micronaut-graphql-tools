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
package io.micronaut.graphql.tools.client.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.exceptions.GraphQLClientException;
import io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException;
import io.micronaut.graphql.tools.client.internal.serde.GraphQLCodec;
import io.micronaut.http.HttpResponse;

/**
 * Decides, from the status and the body, whether an HTTP response is a GraphQL response or a transport failure.
 */
@Internal
final class ResponseClassifier {

    private static final int MAX_CAUSES = 8;

    private ResponseClassifier() {
    }

    static GraphQLResponse<?> classify(GraphQLCodec codec, Argument<?> responseType, boolean unwrap, HttpResponse<?> http,
                                       @Nullable byte[] body) {
        int status = http.code();
        boolean success = status >= 200 && status < 300;
        boolean failure = status >= 400 && status < 600 && status != 404;
        if (!(success || failure)) {
            throw notGraphQL(http, status == 404 ? "404 means that the endpoint does not exist" : "this status never carries a GraphQL response", null);
        }
        if (!startsObject(body)) {
            throw notGraphQL(http, "the body is not a GraphQL response", null);
        }
        GraphQLResponse<?> response;
        try {
            response = codec.decode(body, responseType, unwrap);
        } catch (GraphQLClientException e) {
            if (success) {
                throw e;
            }
            throw notGraphQL(http, "the body is not a GraphQL response", e);
        }
        if (!response.hasData() && response.errors().isEmpty()) {
            throw notGraphQL(http, "the body has neither data nor errors", null);
        }
        return response.withHttpResponse(http);
    }

    static GraphQLClientException transportFailure(Throwable failure) {
        if (failure instanceof GraphQLClientException e) {
            return e;
        }
        boolean timeout = false;
        Throwable cause = failure;
        for (int i = 0; i < MAX_CAUSES && cause != null && !timeout; i++, cause = cause.getCause()) {
            timeout = cause.getClass().getSimpleName().endsWith("TimeoutException");
        }
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return new GraphQLTransportException((timeout ? "The call timed out: " : "The call failed: ") + message,
                GraphQLTransportException.NO_STATUS, null, timeout, failure);
    }

    private static GraphQLTransportException notGraphQL(HttpResponse<?> http, String reason, @Nullable Throwable cause) {
        return new GraphQLTransportException("The server answered " + http.code() + ": " + reason, http.code(), http.getHeaders(), false, cause);
    }

    private static boolean startsObject(@Nullable byte[] body) {
        if (body == null) {
            return false;
        }
        int i = 0;
        if (body.length >= 3 && (body[0] & 0xFF) == 0xEF && (body[1] & 0xFF) == 0xBB && (body[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < body.length && (body[i] == ' ' || body[i] == '\n' || body[i] == '\r' || body[i] == '\t')) {
            i++;
        }
        return i < body.length && body[i] == '{';
    }
}
