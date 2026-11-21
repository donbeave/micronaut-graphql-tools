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
package io.micronaut.graphql.tools.upload.internal;

import io.micronaut.configuration.graphql.GraphQLInvocationData;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the parts of a request of the GraphQL multipart request specification into the operation to run: the {@code operations}
 * part holds the query, the operation name and the variables, with {@code null} where a file goes; the {@code map} part names,
 * for each file part, the paths in the operations that the file replaces.
 */
@Internal
final class MultipartRequestReader {

    static final String OPERATIONS = "operations";
    static final String MAP = "map";
    private static final String VARIABLES = "variables";

    private static final Object MISSING = new Object();

    private final JsonMapper jsonMapper;

    MultipartRequestReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * @param parts the parts of the request, by name
     * @return the operation, with the files in its variables
     * @throws InvalidMultipartRequestException when the request does not follow the specification
     */
    GraphQLInvocationData read(Map<String, CompletedPart> parts) {
        Object operations = json(part(parts, OPERATIONS), OPERATIONS);
        if (operations instanceof List) {
            throw new InvalidMultipartRequestException("The 'operations' part is an array: a batch of operations is not supported, "
                    + "send one operation.");
        }
        if (!(operations instanceof Map<?, ?> operation)) {
            throw new InvalidMultipartRequestException("The 'operations' part must be a JSON object with a 'query'.");
        }
        Object map = json(part(parts, MAP), MAP);
        if (!(map instanceof Map<?, ?> files)) {
            throw new InvalidMultipartRequestException("The 'map' part must be a JSON object: a part name for each file, with the "
                    + "list of paths that the file goes to.");
        }
        String query = text(operation.get("query"));
        if (query == null || query.isBlank()) {
            throw new InvalidMultipartRequestException("The 'operations' part has no 'query': send the query as a string.");
        }
        Map<String, Object> variables = variables(operation.get(VARIABLES));
        for (Map.Entry<?, ?> file : files.entrySet()) {
            String name = String.valueOf(file.getKey());
            CompletedPart upload = parts.get(name);
            if (upload == null) {
                throw new InvalidMultipartRequestException("The 'map' part names the part '" + name + "', and the request has no such part.");
            }
            if (!(file.getValue() instanceof List<?> paths) || paths.isEmpty()) {
                throw new InvalidMultipartRequestException("The 'map' entry of '" + name + "' must be a list of paths, for example "
                        + "[\"variables.file\"].");
            }
            for (Object path : paths) {
                place(variables, path, upload);
            }
        }
        return new GraphQLInvocationData(query, text(operation.get("operationName")), variables);
    }

    private static CompletedPart part(Map<String, CompletedPart> parts, String name) {
        CompletedPart part = parts.get(name);
        if (part == null) {
            throw new InvalidMultipartRequestException("The request has no '" + name + "' part.");
        }
        return part;
    }

    private Object json(CompletedPart part, String name) {
        try {
            return jsonMapper.readValue(part.getBytes(), Object.class);
        } catch (IOException | RuntimeException e) {
            throw new InvalidMultipartRequestException("The '" + name + "' part is not valid JSON.");
        }
    }

    @Nullable
    private static String text(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        throw new InvalidMultipartRequestException("'query' and 'operationName' of the 'operations' part must be strings.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> variables(@Nullable Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) copy(map);
        }
        throw new InvalidMultipartRequestException("'variables' of the 'operations' part must be a JSON object.");
    }

    /** The containers of the parsed JSON are copied, so that placing a file never depends on what the mapper returned. */
    private static Object copy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, element) -> copy.put(String.valueOf(key), copy(element)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(copy(element));
            }
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static void place(Map<String, Object> variables, Object path, CompletedPart upload) {
        if (!(path instanceof String text)) {
            throw new InvalidMultipartRequestException("Every path of the 'map' part must be a string, for example \"variables.file\".");
        }
        String[] segments = text.split("\\.", -1);
        if (segments.length < 2 || !VARIABLES.equals(segments[0])) {
            throw new InvalidMultipartRequestException("The path '" + text + "' must start with 'variables.' and name a variable.");
        }
        Object container = variables;
        for (int i = 1; i < segments.length - 1; i++) {
            container = child(container, segments[i], text);
        }
        String leaf = segments[segments.length - 1];
        if (container instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) container;
            requirePlaceholder(map.containsKey(leaf) ? map.get(leaf) : MISSING, text);
            map.put(leaf, upload);
        } else {
            List<Object> list = (List<Object>) container;
            int index = index(list, leaf, text);
            requirePlaceholder(list.get(index), text);
            list.set(index, upload);
        }
    }

    private static void requirePlaceholder(@Nullable Object current, String path) {
        if (current == MISSING) {
            throw new InvalidMultipartRequestException("The path '" + path + "' has no null placeholder in the variables: add the key "
                    + "with the value null.");
        }
        if (current != null) {
            throw new InvalidMultipartRequestException("The path '" + path + "' is not a null placeholder: it holds a value, or "
                    + "the 'map' part names it twice.");
        }
    }

    private static Object child(Object container, String segment, String path) {
        Object child;
        if (container instanceof Map<?, ?> map) {
            child = map.get(segment);
        } else if (container instanceof List<?> list) {
            child = list.get(index(list, segment, path));
        } else {
            child = null;
        }
        if (!(child instanceof Map) && !(child instanceof List)) {
            throw new InvalidMultipartRequestException("The path '" + path + "' does not exist in the variables: '" + segment
                    + "' is not an object or a list.");
        }
        return child;
    }

    private static int index(List<?> list, String segment, String path) {
        int index;
        try {
            index = Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            throw new InvalidMultipartRequestException("The path '" + path + "' does not exist in the variables: '" + segment
                    + "' is not a list index.");
        }
        if (index < 0 || index >= list.size()) {
            throw new InvalidMultipartRequestException("The path '" + path + "' does not exist in the variables: the list has no index "
                    + index + ".");
        }
        return index;
    }

}
