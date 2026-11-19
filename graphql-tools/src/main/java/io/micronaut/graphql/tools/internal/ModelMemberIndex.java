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
package io.micronaut.graphql.tools.internal;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.graphql.tools.annotation.GraphQLField;
import io.micronaut.graphql.tools.binding.internal.rules.FieldNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The properties and methods of a model class by the name of the field they bind: their own name, or the value of
 * {@code @GraphQLField}. A getter or setter that carries the value renames its property: some compilers copy the
 * annotation of a property to its accessors.
 */
@Internal
final class ModelMemberIndex {

    private final Map<String, List<BeanProperty<Object, Object>>> properties = new HashMap<>();
    private final Map<String, List<BeanMethod<Object, ?>>> methods = new HashMap<>();

    ModelMemberIndex(BeanIntrospection<Object> introspection) {
        Set<String> propertyNames = new HashSet<>();
        introspection.getBeanProperties().forEach(it -> propertyNames.add(it.getName()));
        Map<String, String> accessorRenames = new HashMap<>();
        for (BeanMethod<Object, ?> method : introspection.getBeanMethods()) {
            String declared = declared(method.getAnnotationMetadata());
            String property = FieldNames.accessedProperty(method.getName(), method.getArguments().length);
            if (declared != null && property != null && propertyNames.contains(property)) {
                // the accessor of a property that carries the name renames the property, it binds no field itself
                accessorRenames.put(property, declared);
                continue;
            }
            methods.computeIfAbsent(FieldNames.ofMember(method.getName(), declared), it -> new ArrayList<>())
                    .add(method);
        }
        for (BeanProperty<Object, Object> property : introspection.getBeanProperties()) {
            String declared = declared(property.getAnnotationMetadata());
            if (declared == null) {
                declared = accessorRenames.get(property.getName());
            }
            properties.computeIfAbsent(FieldNames.ofMember(property.getName(), declared), it -> new ArrayList<>())
                    .add(property);
        }
    }

    List<BeanProperty<Object, Object>> properties(String fieldName) {
        return properties.getOrDefault(fieldName, List.of());
    }

    List<BeanMethod<Object, ?>> methods(String fieldName) {
        return methods.getOrDefault(fieldName, List.of());
    }

    private static String declared(AnnotationMetadata metadata) {
        return metadata.stringValue(GraphQLField.class).orElse(null);
    }
}
