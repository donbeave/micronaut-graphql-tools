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
package io.micronaut.graphql.tools;

import io.micronaut.core.annotation.Internal;

import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.Set;

/**
 * The directives graphql-java treats as built in, by name. graphql-java 26 removed {@code DirectiveInfo}, and
 * {@code Directives.isBuiltInDirective} does not exist before 26, so the names are kept here. They are the names of
 * {@code DirectiveInfo.GRAPHQL_SPECIFICATION_DIRECTIVES} on 25.x and of {@code Directives.BUILT_IN_DIRECTIVES} on 26.x;
 * 21.x and 22.x do not list {@code defer} and {@code experimental_disableErrorPropagation} there. {@link #isSpecified}
 * is the set graphql-java lists on every line (reachability, undeclared checks); {@link #isBuiltIn} adds
 * those two (the wiring check); {@link #isProvided} is the active line's own set (the schema hash).
 */
@Internal
final class GraphQLSpecifiedDirectives {

    static final Set<String> NAMES = Set.of(
            "include",
            "skip",
            "deprecated",
            "specifiedBy",
            "oneOf",
            "defer",
            "experimental_disableErrorPropagation"
    );

    static final Set<String> SPECIFIED = Set.of("include", "skip", "deprecated", "specifiedBy", "oneOf");

    private static final Set<String> PROVIDED =
            Set.copyOf(new TypeDefinitionRegistry().getDirectiveDefinitions().keySet());

    private GraphQLSpecifiedDirectives() {
    }

    /**
     * @param name the name of a directive, without the {@code @}
     * @return true if every supported graphql-java line lists a directive with this name as specified
     */
    static boolean isSpecified(String name) {
        return name != null && SPECIFIED.contains(name);
    }

    /**
     * @param name the name of a directive, without the {@code @}
     * @return true if graphql-java implements a directive with this name itself on some supported line
     */
    static boolean isBuiltIn(String name) {
        return name != null && NAMES.contains(name);
    }

    /**
     * @param name the name of a directive, without the {@code @}
     * @return true if the active graphql-java line registers a definition with this name in every registry,
     * or lists it as specified
     */
    static boolean isProvided(String name) {
        return name != null && (SPECIFIED.contains(name) || PROVIDED.contains(name));
    }
}
