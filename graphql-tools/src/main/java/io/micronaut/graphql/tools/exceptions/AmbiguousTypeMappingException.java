/*
 * Copyright 2021-2022 original authors
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

/**
 * Thrown when the {@code SchemaMappingDictionary} maps GraphQL object types so that one Java object would match more
 * than one of them: one type is mapped to a Java interface and another one to a class implementing it.
 *
 * @since 1.0.0
 */
public final class AmbiguousTypeMappingException extends RuntimeException {

    private final transient List<String> graphQlTypes;

    /**
     * @param conflicts    one line per ambiguous pair of GraphQL types, naming both types and both Java types
     * @param graphQlTypes the GraphQL types involved, sorted
     */
    public AmbiguousTypeMappingException(List<String> conflicts, List<String> graphQlTypes) {
        super("The SchemaMappingDictionary maps GraphQL object types ambiguously:\n  "
                + String.join("\n  ", conflicts)
                + "\nAn object of the implementing class would match both GraphQL types. Map each GraphQL object type "
                + "to its own class, or map only one of them to the interface.");
        this.graphQlTypes = List.copyOf(graphQlTypes);
    }

    /**
     * @return the GraphQL types involved, sorted
     */
    public List<String> getGraphQlTypes() {
        return graphQlTypes;
    }

}
