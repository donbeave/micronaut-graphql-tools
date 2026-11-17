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

import io.micronaut.graphql.tools.GraphQLScalarMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thrown when the {@link GraphQLScalarMapping} beans contradict each other or the library: a scalar is mapped more
 * than once, or a mapping replaces a scalar that is built in (GraphQL's own scalars and the extended scalars the library
 * registers itself: Long, Short, BigDecimal, BigInteger).
 */
public final class InvalidScalarMappingException extends RuntimeException {

    private final Map<String, Integer> duplicates;
    private final List<String> builtIn;

    /**
     * @param duplicates the names of scalars that are mapped more than once, with the number of mappings
     * @param builtIn    the names of built-in scalars that are mapped
     */
    public InvalidScalarMappingException(Map<String, Integer> duplicates, List<String> builtIn) {
        super(message(duplicates, builtIn));
        this.duplicates = Map.copyOf(duplicates);
        this.builtIn = List.copyOf(builtIn);
    }

    private static String message(Map<String, Integer> duplicates, List<String> builtIn) {
        List<String> problems = new ArrayList<>();
        duplicates.forEach((name, count) -> problems.add("the scalar " + name + " is mapped " + count + " times"));
        if (builtIn.size() == 1) {
            problems.add("the scalar " + builtIn.get(0) + " is built in");
        } else if (!builtIn.isEmpty()) {
            problems.add("the scalars " + String.join(", ", builtIn) + " are built in");
        }
        return "Invalid " + GraphQLScalarMapping.class.getName() + " beans: " + String.join("; ", problems) + ".";
    }

    /**
     * @return the names of scalars that are mapped more than once, with the number of mappings
     */
    public Map<String, Integer> getDuplicates() {
        return duplicates;
    }

    /**
     * @return the names of built-in scalars that are mapped
     */
    public List<String> getBuiltIn() {
        return builtIn;
    }

}
