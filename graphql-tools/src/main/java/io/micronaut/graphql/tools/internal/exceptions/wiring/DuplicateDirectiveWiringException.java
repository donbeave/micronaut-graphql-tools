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
package io.micronaut.graphql.tools.internal.exceptions.wiring;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;

/**
 * Thrown when more than one {@link io.micronaut.graphql.tools.directive.SchemaDirective} bean binds a wiring to the same
 * directive name: graphql-java keeps only one wiring per name, so the others would be dropped silently. It is also
 * thrown when one directive is wired by both a named bean and an every-element bean, because the directive would
 * take effect twice. Several every-element beans for one directive are allowed.
 */
@Internal
public final class DuplicateDirectiveWiringException extends SchemaBindingException {

    private final List<String> directiveNames;

    public DuplicateDirectiveWiringException(Map<String, List<String>> wirings) {
        super(BindingCode.DUPLICATE_DIRECTIVE_WIRING, wirings.entrySet().stream()
                .map(it -> "More than one SchemaDirective bean wires directive @" + it.getKey() + ": "
                        + String.join(", ", it.getValue()) + ".")
                .collect(Collectors.joining("\n"))
                + " Keep one.");

        this.directiveNames = List.copyOf(wirings.keySet());
    }

    /**
     * @param wirings              directive name to the wirings that apply it, each described with its kind and class
     * @param namedAndEveryElement {@code true}: each directive is wired by a named bean and an every-element bean
     */
    public DuplicateDirectiveWiringException(Map<String, List<String>> wirings, boolean namedAndEveryElement) {
        super(BindingCode.DUPLICATE_DIRECTIVE_WIRING, wirings.entrySet().stream()
                .map(it -> "Directive @" + it.getKey() + " is wired by both a named and an every-element "
                        + "SchemaDirective bean, so it would take effect twice: "
                        + String.join(", ", it.getValue()) + ".")
                .collect(Collectors.joining("\n"))
                + " Use one of them: remove the named bean, or make the every-element wiring stop handling the directive and then drop its name.");

        this.directiveNames = List.copyOf(wirings.keySet());
    }

    public List<String> getDirectiveNames() {
        return directiveNames;
    }

}
