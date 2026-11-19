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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Renders Java types the way {@link java.lang.reflect.Type#getTypeName()} renders them (binary class names, {@code $}
 * for nested classes, {@code ", "} between type arguments, {@code []} for arrays), from Micronaut's compile-time
 * {@link Argument}, never by reflection, so that the text is the same on the JVM and in a native image. The
 * {@link Argument} keeps no wildcards ({@code List<? extends Book>} is rendered as {@code java.util.List<Book>}), no raw
 * types ({@code List} as {@code java.util.List<java.lang.Object>}) and no type arguments of array components
 * ({@code List<String>[]} as {@code java.util.List[]}).
 */
@Internal
public final class JavaTypeNames {

    private JavaTypeNames() {
    }

    public static String parameters(Executable<?, ?> executable) {
        return Arrays.stream(executable.getArguments()).map(JavaTypeNames::of)
                .collect(Collectors.joining(", ", "(", ")"));
    }

    public static String of(Argument<?> argument) {
        Class<?> type = argument.getType();
        Argument<?>[] typeParameters = argument.getTypeParameters();
        if (type.isArray() || typeParameters.length == 0) {
            return type.getTypeName();
        }
        return type.getTypeName() + Arrays.stream(typeParameters)
                .map(JavaTypeNames::of)
                .collect(Collectors.joining(", ", "<", ">"));
    }

}
