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
package io.micronaut.graphql.tools.binding.internal.rules;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.List;

/**
 * The names of the fields a member can bind. The startup and the compile-time checks name a member's fields with it.
 */
@Internal
public final class FieldNames {

    private FieldNames() {
    }

    /**
     * @param memberName the name of a property or a method of a type class
     * @param declared   the value of {@code @GraphQLField}, or {@code null}
     * @return the name of the field the member binds: the declared name, else the name of the member
     */
    public static String ofMember(String memberName, @Nullable String declared) {
        return declared == null || declared.isEmpty() ? memberName : declared;
    }

    /**
     * @param methodName       the name of a resolver method
     * @param accessorPrefixes whether {@code getX} and {@code isX} also name the field {@code x}
     * @return the names of the fields a method without a declared coordinate can bind: its own, then the unprefixed one
     */
    public static List<String> ofResolverMethod(String methodName, boolean accessorPrefixes) {
        if (accessorPrefixes) {
            String unprefixed = unprefixed(methodName);
            if (unprefixed != null) {
                return List.of(methodName, unprefixed);
            }
        }
        return List.of(methodName);
    }

    /**
     * @param methodName     a method name
     * @param parameterCount the number of parameters
     * @return the property a getter ({@code getX}, {@code isX}, no parameter) or a setter ({@code setX}, one parameter)
     * reads or writes, or {@code null} for any other method
     */
    @Nullable
    public static String accessedProperty(String methodName, int parameterCount) {
        if (parameterCount == 0) {
            return unprefixed(methodName);
        }
        if (parameterCount == 1 && methodName.startsWith("set")) {
            return propertyName(methodName.substring(3));
        }
        return null;
    }

    /**
     * @param methodName a method name
     * @return the name without a {@code get} or {@code is} prefix, in the form of a bean property name, or {@code null}
     * when the name has no such prefix followed by an upper-case letter
     */
    @Nullable
    public static String unprefixed(String methodName) {
        int prefix = methodName.startsWith("get") ? 3 : methodName.startsWith("is") ? 2 : 0;
        if (prefix == 0 || methodName.length() == prefix || !Character.isUpperCase(methodName.charAt(prefix))) {
            return null;
        }
        return propertyName(methodName.substring(prefix));
    }

    @Nullable
    private static String propertyName(String rest) {
        if (rest.isEmpty() || !Character.isUpperCase(rest.charAt(0))) {
            return null;
        }
        if (rest.length() > 1 && Character.isUpperCase(rest.charAt(1))) {
            return rest;
        }
        return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
    }
}
