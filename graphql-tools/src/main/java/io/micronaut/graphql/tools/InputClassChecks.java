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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What the input-type binding phase found out about the input classes: the result the phase returns and the generator
 * reads. Immutable.
 *
 * @param unmappedInputProperties the properties no input field maps to, by input type name
 * @param ignoredInputProperties  the properties marked as ignored
 */
@Internal
record InputClassChecks(Map<String, Map.Entry<Class<?>, List<String>>> unmappedInputProperties,
                        Set<String> ignoredInputProperties) {

    InputClassChecks {
        unmappedInputProperties = Collections.unmodifiableSortedMap(new TreeMap<>(unmappedInputProperties));
        ignoredInputProperties = Collections.unmodifiableSortedSet(new TreeSet<>(ignoredInputProperties));
    }

    void checkCollected(boolean failOnUnmappedProperties, BindingManifestRecorder manifestRecorder) {
        ignoredInputProperties.forEach(manifestRecorder::ignored);

        unmappedInputProperties.values().forEach(entry -> entry.getValue().forEach(property ->
                manifestRecorder.diagnostic(BindingCode.UNMAPPED_INPUT_PROPERTY, entry.getKey().getTypeName() + "#" + property)));

        if (failOnUnmappedProperties && !unmappedInputProperties.isEmpty()) {
            throw new UnmappedInputPropertiesException(unmappedInputProperties);
        }
    }

    /**
     * Collects the findings while the input types are bound. Owned by the binder; only {@link #build()} leaves it.
     */
    static final class Builder {

        private final Map<String, Map.Entry<Class<?>, List<String>>> unmapped = new TreeMap<>();
        private final Set<String> ignored = new TreeSet<>();

        void collect(InputObjectTypeDefinition inputObjectTypeDefinition, JClass inputClass, Class<?> beanType) {
            InputVerdict verdict = InputTypeRules.check(inputObjectTypeDefinition.getInputValueDefinitions().stream()
                    .map(InputValueDefinition::getName)
                    .toList(), inputClass);
            ignored.addAll(verdict.ignoredProperties());
            if (!verdict.unmappedProperties().isEmpty()) {
                unmapped.put(inputObjectTypeDefinition.getName(),
                        Map.entry(beanType, List.copyOf(verdict.unmappedProperties())));
            }
        }

        InputClassChecks build() {
            return new InputClassChecks(unmapped, ignored);
        }
    }

}
