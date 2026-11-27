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
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Decodes an {@link UnwrappedResponse}: the {@code data} must be an object with one field, and the value of that field is
 * decoded as the type argument.
 */
@Internal
@Singleton
final class UnwrappedResponseDeserializer implements Deserializer<UnwrappedResponse<?>> {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public UnwrappedResponse<?> deserialize(Decoder decoder, DecoderContext context, Argument<? super UnwrappedResponse<?>> type)
            throws IOException {
        return new UnwrappedResponse(ResponseReader.read(decoder, context, UnwrappedResponse.dataType(type), true));
    }
}
