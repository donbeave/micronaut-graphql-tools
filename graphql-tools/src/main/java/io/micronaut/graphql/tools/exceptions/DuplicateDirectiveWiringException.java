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
 * Thrown when more than one {@link io.micronaut.graphql.tools.SchemaDirective} bean binds a wiring to the same
 * directive name: graphql-java keeps only one wiring per name, so the others would be dropped silently.
 */
public final class DuplicateDirectiveWiringException extends RuntimeException {

    private final List<String> directiveNames;

    /**
     * @param wirings directive name to the classes of its wirings, in the order to report them
     */
    public DuplicateDirectiveWiringException(Map<String, List<String>> wirings) {
        super(wirings.entrySet().stream()
                .map(it -> "More than one SchemaDirective bean wires directive @" + it.getKey() + ": "
                        + String.join(", ", it.getValue()) + ".")
                .collect(Collectors.joining("\n"))
                + " Keep one.");

        this.directiveNames = List.copyOf(wirings.keySet());
    }

    /**
     * @return the directive names that have more than one wiring
     */
    public List<String> getDirectiveNames() {
        return directiveNames;
    }

}
