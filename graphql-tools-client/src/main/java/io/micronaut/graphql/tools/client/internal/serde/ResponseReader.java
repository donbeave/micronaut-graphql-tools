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
import io.micronaut.graphql.tools.client.GraphQLError;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the envelope of a GraphQL response: {@code data} with its presence, {@code errors} and {@code extensions}. Unknown
 * entries are skipped.
 */
@Internal
final class ResponseReader {

    private ResponseReader() {
    }

    static GraphQLResponse<Object> read(Decoder decoder, Deserializer.DecoderContext context, Argument<?> dataType, boolean unwrap)
            throws IOException {
        Decoder object = decoder.decodeObject();
        Object data = null;
        boolean hasData = false;
        List<GraphQLError> errors = List.of();
        Map<String, Object> extensions = Map.of();
        for (String key = object.decodeKey(); key != null; key = object.decodeKey()) {
            switch (key) {
                case "data" -> {
                    hasData = true;
                    data = unwrap ? readUnwrapped(object, context, dataType) : readData(object, context, dataType);
                }
                case "errors" -> {
                    errors = readErrors(object);
                }
                case "extensions" -> {
                    extensions = readMap(object);
                }
                default -> object.skipValue();
            }
        }
        object.finishStructure();
        return new GraphQLResponse<>(data, hasData, errors, extensions, null);
    }

    @SuppressWarnings("unchecked")
    private static Object readData(Decoder decoder, Deserializer.DecoderContext context, Argument<?> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        Deserializer<Object> deserializer = specific(context, type);
        return deserializer.deserialize(decoder, context, (Argument<Object>) type);
    }

    private static Object readUnwrapped(Decoder decoder, Deserializer.DecoderContext context, Argument<?> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        Decoder data = decoder.decodeObject();
        String field = data.decodeKey();
        if (field == null) {
            throw decoder.createDeserializationException("The data has no root field to unwrap", null);
        }
        Object value = readData(data, context, type);
        if (data.decodeKey() != null) {
            throw decoder.createDeserializationException("The data has more than one root field, so it cannot be unwrapped", null);
        }
        data.finishStructure();
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Deserializer<Object> specific(Deserializer.DecoderContext context, Argument<?> type) throws IOException {
        Deserializer<Object> found = (Deserializer<Object>) context.findDeserializer(type);
        return found.createSpecific(context, (Argument<? super Object>) type);
    }

    private static List<GraphQLError> readErrors(Decoder decoder) throws IOException {
        if (decoder.decodeNull()) {
            return List.of();
        }
        Decoder array = decoder.decodeArray();
        List<GraphQLError> errors = new ArrayList<>();
        while (array.hasNextArrayValue()) {
            errors.add(readError(array));
        }
        array.finishStructure();
        return errors;
    }

    private static GraphQLError readError(Decoder decoder) throws IOException {
        Decoder object = decoder.decodeObject();
        String message = null;
        List<GraphQLError.Location> locations = List.of();
        List<Object> path = List.of();
        Map<String, Object> extensions = Map.of();
        for (String key = object.decodeKey(); key != null; key = object.decodeKey()) {
            switch (key) {
                case "message" -> {
                    message = object.decodeStringNullable();
                }
                case "locations" -> {
                    locations = readLocations(object);
                }
                case "path" -> {
                    path = readPath(object);
                }
                case "extensions" -> {
                    extensions = readMap(object);
                }
                default -> object.skipValue();
            }
        }
        object.finishStructure();
        return new GraphQLError(message, locations, path, extensions);
    }

    private static List<GraphQLError.Location> readLocations(Decoder decoder) throws IOException {
        if (decoder.decodeNull()) {
            return List.of();
        }
        Decoder array = decoder.decodeArray();
        List<GraphQLError.Location> locations = new ArrayList<>();
        while (array.hasNextArrayValue()) {
            Decoder object = array.decodeObject();
            int line = 0;
            int column = 0;
            for (String key = object.decodeKey(); key != null; key = object.decodeKey()) {
                switch (key) {
                    case "line" -> {
                        line = object.decodeInt();
                    }
                    case "column" -> {
                        column = object.decodeInt();
                    }
                    default -> object.skipValue();
                }
            }
            object.finishStructure();
            locations.add(new GraphQLError.Location(line, column));
        }
        array.finishStructure();
        return locations;
    }

    private static List<Object> readPath(Decoder decoder) throws IOException {
        if (decoder.decodeNull()) {
            return List.of();
        }
        Decoder array = decoder.decodeArray();
        List<Object> path = new ArrayList<>();
        while (array.hasNextArrayValue()) {
            Object segment = array.decodeArbitrary();
            path.add(segment instanceof Number number ? (Object) number.intValue() : segment);
        }
        array.finishStructure();
        return path;
    }

    private static Map<String, Object> readMap(Decoder decoder) throws IOException {
        if (decoder.decodeNull()) {
            return Map.of();
        }
        Object value = decoder.decodeArbitrary();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return copy;
        }
        throw decoder.createDeserializationException("Expected an object", value);
    }
}
