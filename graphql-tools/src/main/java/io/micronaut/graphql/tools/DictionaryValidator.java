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

import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.exceptions.AmbiguousTypeMappingException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The check of the {@link SchemaMappingDictionary}: two entries must not map one type to an interface and another to a
 * class that implements it.
 */
@Internal
final class DictionaryValidator {

    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final SchemaMappingDictionary schemaMappingDictionary;

    DictionaryValidator(TypeDefinitionRegistry typeDefinitionRegistry, SchemaMappingDictionary schemaMappingDictionary) {
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.schemaMappingDictionary = schemaMappingDictionary;
    }

    void validate() {
        List<Map.Entry<String, Class<?>>> entries = schemaMappingDictionary.getTypes().entrySet().stream()
                .filter(it -> typeDefinitionRegistry.getType(it.getKey())
                        .filter(ObjectTypeDefinition.class::isInstance)
                        .isPresent())
                .sorted(Map.Entry.comparingByKey())
                .toList();

        List<String> conflicts = new ArrayList<>();
        Set<String> graphQlTypes = new TreeSet<>();

        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                Map.Entry<String, Class<?>> first = entries.get(i);
                Map.Entry<String, Class<?>> second = entries.get(j);

                String conflict = describeAmbiguity(first, second);
                if (conflict == null) {
                    conflict = describeAmbiguity(second, first);
                }
                if (conflict != null) {
                    conflicts.add(conflict);
                    graphQlTypes.add(first.getKey());
                    graphQlTypes.add(second.getKey());
                }
            }
        }

        if (!conflicts.isEmpty()) {
            throw new AmbiguousTypeMappingException(conflicts, new ArrayList<>(graphQlTypes));
        }
    }

    @Nullable
    private String describeAmbiguity(Map.Entry<String, Class<?>> interfaceEntry, Map.Entry<String, Class<?>> other) {
        Class<?> interfaceClass = interfaceEntry.getValue();
        Class<?> otherClass = other.getValue();

        if (!interfaceClass.isInterface() || !interfaceClass.isAssignableFrom(otherClass)) {
            return null;
        }

        return "`" + interfaceEntry.getKey() + "` is mapped to the interface " + interfaceClass.getName() + " and `"
                + other.getKey() + "` to " + otherClass.getName() + ", which implements it";
    }

}
