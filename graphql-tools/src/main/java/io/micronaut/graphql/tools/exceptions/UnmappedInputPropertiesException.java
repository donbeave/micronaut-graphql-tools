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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thrown when input classes have settable properties that their GraphQL input types do not define, so no request can
 * ever set them. {@code micronaut.graphql.tools.checks.unmapped-input-property} set to {@code warn} or {@code ignore} accepts them instead.
 */
public final class UnmappedInputPropertiesException extends SchemaBindingException {

    private final Map<String, Map.Entry<Class<?>, List<String>>> unmappedProperties;

    public UnmappedInputPropertiesException(Map<String, Map.Entry<Class<?>, List<String>>> unmappedProperties) {
        super(BindingCode.UNMAPPED_INPUT_PROPERTY, "Input classes have properties that their GraphQL input types do not define "
                + "(set micronaut.graphql.tools.checks.unmapped-input-property to warn or ignore to accept them):"
                + unmappedProperties.entrySet().stream()
                .map(it -> "\n  GraphQL input object type: " + it.getKey()
                        + ", mapped class: " + it.getValue().getKey().getName()
                        + ", properties: " + String.join(", ", it.getValue().getValue()))
                .collect(Collectors.joining()));
        this.unmappedProperties = Map.copyOf(unmappedProperties);
    }

    public Map<String, Map.Entry<Class<?>, List<String>>> getUnmappedProperties() {
        return unmappedProperties;
    }

}
