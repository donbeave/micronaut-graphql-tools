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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Map;

/**
 * Encodes a {@link GraphQLRequest} as the body of a request: {@code query}, and when present {@code operationName},
 * {@code variables} and {@code extensions}.
 */
@Internal
@Singleton
final class GraphQLRequestSerializer implements Serializer<GraphQLRequest<?>> {

    private static final Argument<Map<String, Object>> MAP = Argument.mapOf(String.class, Object.class);

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends GraphQLRequest<?>> type, GraphQLRequest<?> value)
            throws IOException {
        Encoder object = encoder.encodeObject(type);
        object.encodeKey("query");
        object.encodeString(value.getDocument());
        if (value.getOperationName() != null) {
            object.encodeKey("operationName");
            object.encodeString(value.getOperationName());
        }
        if (!value.getVariables().isEmpty()) {
            object.encodeKey("variables");
            encodeMap(object, context, value.getVariables());
        }
        if (!value.getExtensions().isEmpty()) {
            object.encodeKey("extensions");
            encodeMap(object, context, value.getExtensions());
        }
        object.finishStructure();
    }

    @SuppressWarnings("unchecked")
    private static void encodeMap(Encoder encoder, EncoderContext context, Map<String, Object> map) throws IOException {
        Serializer<Map<String, Object>> serializer = (Serializer<Map<String, Object>>) context.findSerializer(MAP);
        serializer.createSpecific(context, MAP).serialize(encoder, context, MAP, map);
    }
}
