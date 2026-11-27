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
package io.micronaut.graphql.tools.binding.internal.client.method;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.internal.model.TypeRef;

import java.util.Map;

/**
 * What the client rules ask of the Java names that a type model hands them.
 */
@Internal
final class JavaTypes {

    static final String OBJECT = "java.lang.Object";
    static final String MAP = "java.util.Map";
    static final String ITERABLE = "java.lang.Iterable";
    static final String OPTIONAL = "java.util.Optional";

    private static final Map<String, String> BOXES = Map.of("boolean", "java.lang.Boolean", "byte", "java.lang.Byte",
            "char", "java.lang.Character", "double", "java.lang.Double", "float", "java.lang.Float", "int", "java.lang.Integer",
            "long", "java.lang.Long", "short", "java.lang.Short");

    private static final Map<String, String> OPTIONAL_PRIMITIVES = Map.of("java.util.OptionalInt", "java.lang.Integer",
            "java.util.OptionalLong", "java.lang.Long", "java.util.OptionalDouble", "java.lang.Double");

    private JavaTypes() {
    }

    /**
     * @param name the binary name of a class or a primitive
     * @return the name of the wrapper class of a primitive, the name itself otherwise
     */
    static String box(String name) {
        return BOXES.getOrDefault(name, name);
    }

    /**
     * @param name a binary name
     * @return the name after the last dot or dollar sign
     */
    static String simple(String name) {
        return name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1);
    }

    /**
     * @param type a type
     * @return whether it is an {@code Optional} or one of the primitive optionals, which hold a value or none
     */
    static boolean isOptional(TypeRef type) {
        return !type.array() && (type.rawName().equals(OPTIONAL) || OPTIONAL_PRIMITIVES.containsKey(type.rawName()));
    }

    /**
     * @param optional an optional type
     * @return the type that it holds, null when it is raw
     */
    static TypeRef held(TypeRef optional) {
        String wrapper = OPTIONAL_PRIMITIVES.get(optional.rawName());
        if (wrapper != null) {
            return TypeRef.of(wrapper);
        }
        return optional.typeArguments().isEmpty() ? null : optional.typeArguments().get(0);
    }

    /**
     * @param type a type
     * @return whether the declaration can hold {@code null}: a Kotlin nullable type, or an {@code Optional}
     */
    static boolean mayBeNull(TypeRef type) {
        return type.kotlin() && type.nullability() == io.micronaut.graphql.tools.binding.internal.model.Nullability.NULLABLE
                && !type.primitive();
    }
}
