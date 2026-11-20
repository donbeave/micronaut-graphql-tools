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
package io.micronaut.graphql.tools.binding.internal.model;

import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * A reference to a Java type, as a name and its type arguments. It is a value: resolving it to a {@link JClass} is the
 * job of {@link TypeModel#resolve(TypeRef)}.
 *
 * @param rawName       the binary name of the class, without type arguments (the component type for an array)
 * @param typeArguments the type arguments
 * @param nullability   whether the position accepts {@code null}
 * @param primitive     whether the type is a primitive
 * @param array         whether the type is an array
 * @param kotlin        whether {@code nullability} is the compile-time nullability of a Kotlin type, which the rules check
 *                      against the schema; the nullability of a Java annotation never is
 */
@Internal
public record TypeRef(String rawName, List<TypeRef> typeArguments, Nullability nullability, boolean primitive,
                      boolean array, boolean kotlin) {

    public TypeRef {
        typeArguments = List.copyOf(typeArguments);
    }

    /**
     * @param rawName       the binary name of the class
     * @param typeArguments the type arguments
     * @param nullability   whether the position accepts {@code null}
     * @param primitive     whether the type is a primitive
     * @param array         whether the type is an array
     */
    public TypeRef(String rawName, List<TypeRef> typeArguments, Nullability nullability, boolean primitive, boolean array) {
        this(rawName, typeArguments, nullability, primitive, array, false);
    }

    /**
     * @param rawName the binary name of a class
     * @return a reference without type arguments and without a nullability declaration
     */
    public static TypeRef of(String rawName) {
        return new TypeRef(rawName, List.of(), Nullability.UNKNOWN, false, false);
    }
}
