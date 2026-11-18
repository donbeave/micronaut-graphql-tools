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

import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.rules.InputTypeRules;
import io.micronaut.graphql.tools.binding.internal.rules.InputVerdict;
import io.micronaut.graphql.tools.exceptions.UnmappedInputPropertiesException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Internal
final class InputClassChecks {

    private final Map<String, Map.Entry<Class<?>, List<String>>> unmappedInputProperties = new TreeMap<>();
    private final Set<String> ignoredInputProperties = new TreeSet<>();

    void checkCollected(boolean failOnUnmappedProperties, BindingManifestRecorder manifestRecorder) {
        ignoredInputProperties.forEach(manifestRecorder::ignored);

        unmappedInputProperties.values().forEach(entry -> entry.getValue().forEach(property ->
                manifestRecorder.diagnostic(BindingCode.UNMAPPED_INPUT_PROPERTY, entry.getKey().getTypeName() + "#" + property)));

        if (failOnUnmappedProperties && !unmappedInputProperties.isEmpty()) {
            throw new UnmappedInputPropertiesException(unmappedInputProperties);
        }
    }

    void collectUnmappedInputProperties(InputObjectTypeDefinition inputObjectTypeDefinition, JClass inputClass,
                                        Class<?> beanType) {
        InputVerdict verdict = InputTypeRules.check(inputObjectTypeDefinition.getInputValueDefinitions().stream()
                .map(InputValueDefinition::getName)
                .toList(), inputClass);
        ignoredInputProperties.addAll(verdict.ignoredProperties());
        if (!verdict.unmappedProperties().isEmpty()) {
            unmappedInputProperties.put(inputObjectTypeDefinition.getName(),
                    Map.entry(beanType, verdict.unmappedProperties()));
        }
    }

}
