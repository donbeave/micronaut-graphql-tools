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

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.graphql.tools.GraphQLUtils.getTypeName;

/**
 * The interfaces of an object type that declare a field, by object type and field name. Each object type is read once,
 * when it is first asked about, so the answer for a field is a lookup, not a scan of every interface it implements.
 */
@Internal
final class DeclaringInterfaces {

    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final Map<String, Map<String, List<InterfaceTypeDefinition>>> byObjectType = new HashMap<>();

    DeclaringInterfaces(TypeDefinitionRegistry typeDefinitionRegistry) {
        this.typeDefinitionRegistry = typeDefinitionRegistry;
    }

    /**
     * @param objectTypeDefinition the object type
     * @param fieldName            the name of a field
     * @return the interfaces that the object type implements and that declare the field, by name
     */
    List<InterfaceTypeDefinition> of(ObjectTypeDefinition objectTypeDefinition, String fieldName) {
        return byObjectType.computeIfAbsent(objectTypeDefinition.getName(), it -> index(objectTypeDefinition))
                .getOrDefault(fieldName, List.of());
    }

    private Map<String, List<InterfaceTypeDefinition>> index(ObjectTypeDefinition objectTypeDefinition) {
        Map<String, List<InterfaceTypeDefinition>> result = new HashMap<>();
        objectTypeDefinition.getImplements().stream()
                .map(it -> typeDefinitionRegistry.getType(getTypeName(it).getName(), InterfaceTypeDefinition.class))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(InterfaceTypeDefinition::getName))
                .forEach(iface -> {
                    Set<String> names = new HashSet<>();
                    for (FieldDefinition field : iface.getFieldDefinitions()) {
                        if (names.add(field.getName())) {
                            result.computeIfAbsent(field.getName(), it -> new ArrayList<>()).add(iface);
                        }
                    }
                });
        return result;
    }

}
