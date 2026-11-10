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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thrown when the schema applies directives that have no wiring and are not listed in
 * {@code micronaut.graphql.tools.inert-directives} (such a directive would be in the schema and have no effect), or
 * when that list names directives that the schema does not declare or that have a wiring (a stale or contradictory
 * entry).
 */
public final class UnwiredDirectivesException extends RuntimeException {

    private static final int MAX_COORDINATES = 5;

    private final List<String> unwiredDirectives;
    private final List<String> invalidInertDirectives;

    /**
     * @param unwiredDirectives      directive name to the schema coordinates that apply it, in the order to report them
     * @param invalidInertDirectives listed directive name to the reason it is invalid, in the order to report them
     */
    public UnwiredDirectivesException(Map<String, List<String>> unwiredDirectives,
                                      Map<String, String> invalidInertDirectives) {
        super(message(unwiredDirectives, invalidInertDirectives));

        this.unwiredDirectives = List.copyOf(unwiredDirectives.keySet());
        this.invalidInertDirectives = List.copyOf(invalidInertDirectives.keySet());
    }

    private static String message(Map<String, List<String>> unwiredDirectives,
                                  Map<String, String> invalidInertDirectives) {
        List<String> paragraphs = new ArrayList<>();
        if (!unwiredDirectives.isEmpty()) {
            paragraphs.add("The schema applies directives that have no wiring: "
                    + unwiredDirectives.entrySet().stream()
                    .map(it -> "@" + it.getKey() + " (" + coordinates(it.getValue()) + ")")
                    .collect(Collectors.joining(", "))
                    + ".\n  Wire each one with a SchemaDirective bean that names it (SchemaDirective.named, or "
                    + "SchemaDirective.everyElement for a wiring that sees every element), or list it in "
                    + "micronaut.graphql.tools.inert-directives if it has no effect at runtime.");
        }
        if (!invalidInertDirectives.isEmpty()) {
            paragraphs.add("micronaut.graphql.tools.inert-directives lists directives that are built in, not declared "
                    + "in the schema, or that have a wiring: "
                    + invalidInertDirectives.entrySet().stream()
                    .map(it -> "@" + it.getKey() + " (" + it.getValue() + ")")
                    .collect(Collectors.joining(", "))
                    + ".\n  Remove them from the list.");
        }
        return String.join("\n", paragraphs);
    }

    private static String coordinates(List<String> coordinates) {
        if (coordinates.size() <= MAX_COORDINATES) {
            return String.join(", ", coordinates);
        }
        return String.join(", ", coordinates.subList(0, MAX_COORDINATES))
                + " and " + (coordinates.size() - MAX_COORDINATES) + " more";
    }

    /**
     * @return the names of the applied directives that have no wiring and are not listed as inert
     */
    public List<String> getUnwiredDirectives() {
        return unwiredDirectives;
    }

    /**
     * @return the names listed in {@code micronaut.graphql.tools.inert-directives} that the schema does not declare or
     * that have a wiring
     */
    public List<String> getInvalidInertDirectives() {
        return invalidInertDirectives;
    }

}
