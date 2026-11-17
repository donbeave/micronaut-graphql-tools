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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Thrown when {@code micronaut.graphql.tools.allow-unreachable} names a type that is not an unreachable object,
 * interface or union type of the schema: the entry is stale or misspelled.
 */
public final class InvalidAllowUnreachableException extends RuntimeException {

    private final List<String> undefinedTypeNames;
    private final List<String> notUnreachableTypeNames;

    /**
     * @param undefinedTypeNames      listed names that the schema does not define
     * @param notUnreachableTypeNames listed names that the schema defines, but not as unreachable object, interface
     *                                or union types
     */
    public InvalidAllowUnreachableException(List<String> undefinedTypeNames, List<String> notUnreachableTypeNames) {
        super("micronaut.graphql.tools.allow-unreachable lists types that are not unreachable object, interface or "
                + "union types of the schema: "
                + Stream.concat(
                        undefinedTypeNames.stream().map(it -> it + " (not defined in the schema)"),
                        notUnreachableTypeNames.stream()
                                .map(it -> it + " (not an unreachable object, interface or union type)"))
                .collect(Collectors.joining(", "))
                + ".\n  Remove them from the list.");

        this.undefinedTypeNames = List.copyOf(undefinedTypeNames);
        this.notUnreachableTypeNames = List.copyOf(notUnreachableTypeNames);
    }

    /**
     * @return listed names that the schema does not define
     */
    public List<String> getUndefinedTypeNames() {
        return undefinedTypeNames;
    }

    /**
     * @return listed names that the schema defines, but not as unreachable object, interface or union types
     */
    public List<String> getNotUnreachableTypeNames() {
        return notUnreachableTypeNames;
    }

}
