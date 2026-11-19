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
package io.micronaut.graphql.tools.internal.exceptions;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;

/**
 * Thrown when {@code micronaut.graphql.tools.allow.unreachable} names a type that is not an unreachable type of the
 * schema (an object, interface, union, enum, input or scalar type): the entry is stale or
 * misspelled.
 */
@Internal
public final class InvalidAllowUnreachableException extends SchemaBindingException {

    private final List<String> undefinedTypeNames;
    private final List<String> notUnreachableTypeNames;

    /**
     * @param undefinedTypeNames      listed names that the schema does not define
     * @param notUnreachableTypeNames listed names that the schema defines, but not as unreachable object, interface
     *                                or union types
     */
    public InvalidAllowUnreachableException(List<String> undefinedTypeNames, List<String> notUnreachableTypeNames) {
        this(undefinedTypeNames, notUnreachableTypeNames, "object, interface or union");
    }

    /**
     * @param undefinedTypeNames      listed names that the schema does not define
     * @param notUnreachableTypeNames listed names that the schema defines, but not as unreachable types of the kinds
     *                                that can be allowed
     * @param kinds                   the kinds of types that can be allowed, for the message
     */
    public InvalidAllowUnreachableException(List<String> undefinedTypeNames, List<String> notUnreachableTypeNames,
                                            String kinds) {
        this(undefinedTypeNames, reasons(notUnreachableTypeNames, "not an unreachable " + kinds + " type"), kinds);
    }

    /**
     * @param undefinedTypeNames listed names that the schema does not define
     * @param notUnreachable     listed names that the schema defines, but not as unreachable types of the kinds that can
     *                           be allowed, each with the reason (for example {@code is reachable} or {@code is an enum})
     * @param kinds              the kinds of types that can be allowed, for the message
     */
    public InvalidAllowUnreachableException(List<String> undefinedTypeNames, Map<String, String> notUnreachable,
                                            String kinds) {
        super(BindingCode.INVALID_ALLOW_UNREACHABLE, "micronaut.graphql.tools.allow.unreachable lists types that are not unreachable " + kinds
                + " types of the schema: "
                + Stream.concat(
                        undefinedTypeNames.stream().map(it -> it + " (not defined in the schema)"),
                        notUnreachable.entrySet().stream().map(it -> it.getKey() + " (" + it.getValue() + ")"))
                .collect(Collectors.joining(", "))
                + ".\n  Remove them from the list.");

        this.undefinedTypeNames = List.copyOf(undefinedTypeNames);
        this.notUnreachableTypeNames = List.copyOf(notUnreachable.keySet());
    }

    private static Map<String, String> reasons(List<String> names, String reason) {
        Map<String, String> result = new LinkedHashMap<>();
        names.forEach(it -> result.put(it, reason));
        return result;
    }

    public List<String> getUndefinedTypeNames() {
        return undefinedTypeNames;
    }

    public List<String> getNotUnreachableTypeNames() {
        return notUnreachableTypeNames;
    }

}
