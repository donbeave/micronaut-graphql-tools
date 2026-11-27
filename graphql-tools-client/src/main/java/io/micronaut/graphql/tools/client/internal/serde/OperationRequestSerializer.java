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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Map;

/**
 * Encodes an {@link OperationRequest} as the body of a request, the same bytes that {@link GraphQLRequestSerializer} writes for
 * the same operation: {@code query}, and when present {@code operationName} and {@code variables}. Each variable is written
 * with the serializer of its declared type.
 */
@Internal
@Singleton
final class OperationRequestSerializer implements Serializer<OperationRequest> {

    private static final Argument<Map<String, Object>> MAP = Argument.mapOf(String.class, Object.class);

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends OperationRequest> type, OperationRequest value)
            throws IOException {
        Encoder object = encoder.encodeObject(type);
        object.encodeKey("query");
        object.encodeString(value.document());
        if (value.operationName() != null) {
            object.encodeKey("operationName");
            object.encodeString(value.operationName());
        }
        if (value.variableNames().length > 0) {
            object.encodeKey("variables");
            writeVariables(object, context, value);
        }
        object.finishStructure();
    }

    @SuppressWarnings("unchecked")
    static void writeVariables(Encoder encoder, EncoderContext context, OperationRequest value) throws IOException {
        String[] names = value.variableNames();
        Encoder variables = encoder.encodeObject(MAP);
        for (int i = 0; i < names.length; i++) {
            variables.encodeKey(names[i]);
            Object variable = value.variableValues()[i];
            if (variable == null) {
                variables.encodeNull();
            } else {
                Argument<Object> declared = (Argument<Object>) value.variableTypes()[i];
                context.findSerializer(declared).createSpecific(context, declared).serialize(variables, context, declared, variable);
            }
        }
        variables.finishStructure();
    }
}
