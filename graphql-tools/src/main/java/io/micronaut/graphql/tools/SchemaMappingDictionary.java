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
 * @author Alexey Zhokhov
 */
public class SchemaMappingDictionary {

    private final Map<String, Class<?>> types = new LinkedHashMap<>();

    /**
     * TODO.
     *
     * @return TODO
     */
    public Map<String, Class<?>> getTypes() {
        return Collections.unmodifiableMap(types);
    }

    /**
     * TODO.
     *
     * @param graphqlType
     * @param implementationClass
     * @return TODO
     */
    public SchemaMappingDictionary registerType(@NonNull String graphqlType, @NonNull Class<?> implementationClass) {
        ArgumentUtils.requireNonNull("graphqlType", graphqlType);
        ArgumentUtils.requireNonNull("implementationClass", implementationClass);

        if (
                implementationClass.isInterface()
                        || implementationClass.isEnum()
                        || implementationClass.isArray()
                        || implementationClass.isAnnotation()
        ) {
            throw new IllegalArgumentException(implementationClass + " must be a top level class.");
        }

        if (types.containsKey(graphqlType)) {
            throw new IllegalArgumentException("Duplicated GraphQL type: " + graphqlType);
        }
        if (types.containsValue(implementationClass)) {
            throw new IllegalArgumentException("One GraphQL type can have only one implementation class, found " +
                    "duplicate: " + implementationClass.getName());
        }
        types.put(graphqlType, implementationClass);

        return this;
    }

}
