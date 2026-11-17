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
package io.micronaut.graphql.tools.exceptions;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thrown when the schema defines object, interface or union types that no field reachable from a root operation
 * type returns, so that no Java class is bound to them.
 */
public final class UnreachableTypesException extends RuntimeException {

    private final List<String> typeNames;

    /**
     * @param types the unreachable types: name to kind ({@code type}, {@code interface}, {@code union}), in the order
     *              to report them
     */
    public UnreachableTypesException(Map<String, String> types) {
        super("The schema defines types that no field reachable from a root operation type returns, so nothing "
                + "binds them: "
                + types.entrySet().stream()
                .map(it -> it.getKey() + " (" + it.getValue() + ")")
                .collect(Collectors.joining(", "))
                + ".\n  Bind them through a field, remove them from the schema, list them in "
                + "micronaut.graphql.tools.allow-unreachable, or set micronaut.graphql.tools.unreachable-types to ignore.");

        this.typeNames = List.copyOf(types.keySet());
    }

    /**
     * @return the names of the unreachable types
     */
    public List<String> getTypeNames() {
        return typeNames;
    }

}
