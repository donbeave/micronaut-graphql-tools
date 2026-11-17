/*
 * Copyright 2021-2022 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps GraphQL object types to the Java types that represent them, for the types the schema cannot infer from a
 * declared return type: the members of union types.
 *
 * <p>A GraphQL type can be mapped to a class or to a Java interface. An interface and the classes annotated with
 * {@code @GraphQLType(TheInterface.class)} are one mapping: the GraphQL type binds through the interface. Each GraphQL
 * type is mapped once and each Java type to one GraphQL type. When the schema is built, it is also an error to map one
 * GraphQL object type to a Java interface and another one to a class implementing that interface, because an object
 * of that class would match both.</p>
 *
 * @author Alexey Zhokhov
 */
public class SchemaMappingDictionary {

    private final Map<String, Class<?>> types = new LinkedHashMap<>();

    /**
     * @return the registered mappings, GraphQL type name to Java type, in registration order
     */
    public Map<String, Class<?>> getTypes() {
        return Collections.unmodifiableMap(types);
    }

    /**
     * Maps a GraphQL type to a Java class or interface.
     *
     * @param graphqlType         the name of the GraphQL type
     * @param implementationClass the class or interface that represents it
     * @return this dictionary
     * @throws IllegalArgumentException if the Java type is an enum, an annotation, an array or a primitive, if the
     *                                  GraphQL type is already mapped, or if the Java type is already mapped to another
     *                                  GraphQL type
     */
    public SchemaMappingDictionary registerType(@NonNull String graphqlType, @NonNull Class<?> implementationClass) {
        ArgumentUtils.requireNonNull("graphqlType", graphqlType);
        ArgumentUtils.requireNonNull("implementationClass", implementationClass);

        if (
                implementationClass.isEnum()
                        || implementationClass.isArray()
                        || implementationClass.isAnnotation()
                        || implementationClass.isPrimitive()
        ) {
            throw new IllegalArgumentException("GraphQL type `" + graphqlType + "` can only be mapped to a class or an "
                    + "interface, found " + implementationClass.getName() + ".");
        }

        Class<?> registeredClass = types.get(graphqlType);
        if (registeredClass != null) {
            if (registeredClass.equals(implementationClass)) {
                throw new IllegalArgumentException("GraphQL type `" + graphqlType + "` is already mapped to "
                        + registeredClass.getName() + ".");
            }
            throw new IllegalArgumentException("Unable to map GraphQL type `" + graphqlType + "` to "
                    + implementationClass.getName() + ", as it is already mapped to " + registeredClass.getName() + ".");
        }

        for (Map.Entry<String, Class<?>> entry : types.entrySet()) {
            if (entry.getValue().equals(implementationClass)) {
                throw new IllegalArgumentException("Unable to map GraphQL type `" + graphqlType + "` to "
                        + implementationClass.getName() + ", as GraphQL type `" + entry.getKey() + "` is already "
                        + "mapped to it. One class can only be mapped to one GraphQL type.");
            }
        }

        types.put(graphqlType, implementationClass);

        return this;
    }

}
