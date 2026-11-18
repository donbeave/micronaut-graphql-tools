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

import io.micronaut.graphql.tools.binding.BindingCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thrown when the schema applies directives that have no wiring and are not listed in
 * {@code micronaut.graphql.tools.allow.inert-directives} (such a directive would be in the schema and have no effect)
 * and the check {@code unwired-directive} is {@code fail} (the default), or, in every mode, when that list names directives that the schema does not declare or that have a wiring (a stale or
 * contradictory entry), or when {@code micronaut.graphql.tools.allow.schema-customizer-directives} lists a built-in or
 * undeclared directive. A directive applied on the schema definition or on a directive argument counts as wired only
 * through one of those two lists, because graphql-java calls no wiring there.
 */
public final class UnwiredDirectivesException extends SchemaBindingException {

    private static final int MAX_COORDINATES = 5;

    private final List<String> unwiredDirectives;
    private final List<String> invalidInertDirectives;
    private final List<String> invalidSchemaCustomizerDirectives;

    /**
     * @param unwiredDirectives      directive name to the schema coordinates that apply it, in the order to report them
     * @param invalidInertDirectives listed directive name to the reason it is invalid, in the order to report them
     */
    public UnwiredDirectivesException(Map<String, List<String>> unwiredDirectives,
                                      Map<String, String> invalidInertDirectives) {
        this(unwiredDirectives, invalidInertDirectives, Map.of());
    }

    /**
     * @param unwiredDirectives                 directive name to the schema coordinates that apply it, in the order to report them
     * @param invalidInertDirectives            listed directive name to the reason it is invalid, in the order to report them
     * @param invalidSchemaCustomizerDirectives schema customizer directive name to the reason it is invalid
     */
    public UnwiredDirectivesException(Map<String, List<String>> unwiredDirectives,
                                      Map<String, String> invalidInertDirectives,
                                      Map<String, String> invalidSchemaCustomizerDirectives) {
        super(codeOf(unwiredDirectives, invalidInertDirectives),
                message(unwiredDirectives, invalidInertDirectives, invalidSchemaCustomizerDirectives));

        this.unwiredDirectives = List.copyOf(unwiredDirectives.keySet());
        this.invalidInertDirectives = List.copyOf(invalidInertDirectives.keySet());
        this.invalidSchemaCustomizerDirectives = List.copyOf(invalidSchemaCustomizerDirectives.keySet());
    }

    private static BindingCode codeOf(Map<String, List<String>> unwiredDirectives,
                                      Map<String, String> invalidInertDirectives) {
        if (!unwiredDirectives.isEmpty()) {
            return BindingCode.UNWIRED_DIRECTIVE;
        }
        return invalidInertDirectives.isEmpty()
                ? BindingCode.INVALID_SCHEMA_CUSTOMIZER_DIRECTIVES : BindingCode.INVALID_INERT_DIRECTIVES;
    }

    private static String message(Map<String, List<String>> unwiredDirectives,
                                  Map<String, String> invalidInertDirectives,
                                  Map<String, String> invalidSchemaCustomizerDirectives) {
        List<String> paragraphs = new ArrayList<>();
        if (!unwiredDirectives.isEmpty()) {
            paragraphs.add(describeUnwired(unwiredDirectives));
        }
        if (!invalidInertDirectives.isEmpty()) {
            paragraphs.add(tagAfterFirst(paragraphs, BindingCode.INVALID_INERT_DIRECTIVES,
                    "micronaut.graphql.tools.allow.inert-directives lists directives that are built in, not declared "
                    + "in the schema, or that have a wiring: "
                    + invalidInertDirectives.entrySet().stream()
                    .map(it -> "@" + it.getKey() + " (" + it.getValue() + ")")
                    .collect(Collectors.joining(", "))
                    + ".\n  Remove them from the list."));
        }
        if (!invalidSchemaCustomizerDirectives.isEmpty()) {
            paragraphs.add(tagAfterFirst(paragraphs, BindingCode.INVALID_SCHEMA_CUSTOMIZER_DIRECTIVES,
                    "micronaut.graphql.tools.allow.schema-customizer-directives lists directives that are built in "
                    + "or not declared in the schema: "
                    + invalidSchemaCustomizerDirectives.entrySet().stream()
                    .map(it -> "@" + it.getKey() + " (" + it.getValue() + ")")
                    .collect(Collectors.joining(", "))
                    + ".\n  Remove them from the list."));
        }
        return String.join("\n", paragraphs);
    }

    // the first paragraph is tagged by the base class with the code of the exception
    private static String tagAfterFirst(List<String> paragraphs, BindingCode code, String paragraph) {
        return paragraphs.isEmpty() ? paragraph : code.tag(paragraph);
    }

    /**
     * Describes applied directives without a wiring as this exception does, for the warning that replaces it when
     * the check is not fail.
     *
     * @param unwiredDirectives directive name to the schema coordinates that apply it, in the order to report them
     * @return the directives with up to five coordinates each, and what to do about them
     */
    public static String describeUnwired(Map<String, List<String>> unwiredDirectives) {
        return "The schema applies directives that have no wiring: "
                + unwiredDirectives.entrySet().stream()
                .map(it -> "@" + it.getKey() + " (" + coordinates(it.getValue()) + ")")
                .collect(Collectors.joining(", "))
                + ".\n  Wire each one with a SchemaDirective bean that names it (SchemaDirective.named, or "
                + "SchemaDirective.everyElement for a wiring that sees every element), or list it in "
                + "micronaut.graphql.tools.allow.inert-directives if it has no effect at runtime, or set "
                + "micronaut.graphql.tools.checks.unwired-directive to warn or ignore."
                + (hasWiringlessCoordinate(unwiredDirectives)
                ? "\n  graphql-java calls no wiring for the schema definition (schema) or for directive arguments "
                + "(@name(arg:)), so a directive applied there must be listed in allow.inert-directives, or in "
                + "micronaut.graphql.tools.allow.schema-customizer-directives if a GraphQLSchemaCustomizer handles it."
                : "");
    }

    private static boolean hasWiringlessCoordinate(Map<String, List<String>> unwiredDirectives) {
        return unwiredDirectives.values().stream().flatMap(List::stream)
                .anyMatch(it -> it.equals("schema") || it.startsWith("@"));
    }

    private static String coordinates(List<String> coordinates) {
        if (coordinates.size() <= MAX_COORDINATES) {
            return String.join(", ", coordinates);
        }
        return String.join(", ", coordinates.subList(0, MAX_COORDINATES))
                + " and " + (coordinates.size() - MAX_COORDINATES) + " more";
    }

    public List<String> getUnwiredDirectives() {
        return unwiredDirectives;
    }

    public List<String> getInvalidInertDirectives() {
        return invalidInertDirectives;
    }

    public List<String> getInvalidSchemaCustomizerDirectives() {
        return invalidSchemaCustomizerDirectives;
    }

}
