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

/**
 * Encodes {@link OperationVariables} with the serializer of the declared type of each variable, as the body of a POST request does.
 */
@Internal
@Singleton
final class OperationVariablesSerializer implements Serializer<OperationVariables> {

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends OperationVariables> type, OperationVariables value)
            throws IOException {
        OperationRequestSerializer.writeVariables(encoder, context, value.request());
    }
}
