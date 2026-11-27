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
package io.micronaut.graphql.tools.client.internal.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.GraphQLRequest;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.exceptions.GraphQLClientException;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Encodes requests and decodes responses with the application's Micronaut Serialization {@link JsonMapper}.
 */
@Internal
@Singleton
public final class GraphQLCodec {

    private final JsonMapper mapper;

    GraphQLCodec(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Encodes a request as the JSON body of an HTTP request.
     *
     * @param request the request
     * @return the body
     */
    public byte[] encode(GraphQLRequest<?> request) {
        try {
            return mapper.writeValueAsBytes(Argument.of(GraphQLRequest.class, request.getDataType()), request);
        } catch (IOException e) {
            throw new GraphQLClientException(GraphQLClientException.DECODE_ERROR, "The request cannot be encoded: " + e.getMessage(), e);
        }
    }

    /**
     * Decodes a response.
     *
     * @param body     the JSON body
     * @param dataType the type of the {@code data}
     * @param <D>      the type of the {@code data}
     * @return the response
     */
    @SuppressWarnings("unchecked")
    public <D> GraphQLResponse<D> decode(byte[] body, Argument<D> dataType) {
        try {
            return (GraphQLResponse<D>) mapper.readValue(body, Argument.of(GraphQLResponse.class, dataType));
        } catch (IOException e) {
            throw decodeFailure(e);
        }
    }

    /**
     * Decodes a response whose {@code data} is an object with one root field, and returns the value of that field as the data.
     *
     * @param body     the JSON body
     * @param dataType the type of the root field
     * @param <D>      the type of the root field
     * @return the response
     */
    @SuppressWarnings("unchecked")
    public <D> GraphQLResponse<D> decodeUnwrapped(byte[] body, Argument<D> dataType) {
        try {
            return ((UnwrappedResponse<D>) mapper.readValue(body, Argument.of(UnwrappedResponse.class, dataType))).response();
        } catch (IOException e) {
            throw decodeFailure(e);
        }
    }

    private static GraphQLClientException decodeFailure(IOException e) {
        return new GraphQLClientException(GraphQLClientException.DECODE_ERROR, "The response cannot be decoded: " + e.getMessage(), e);
    }
}
