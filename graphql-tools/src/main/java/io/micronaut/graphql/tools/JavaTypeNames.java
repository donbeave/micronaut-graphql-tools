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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.inject.ExecutableMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Renders Java types the way {@link java.lang.reflect.Type#getTypeName()} renders them (binary class names, {@code $}
 * for nested classes, {@code ", "} between type arguments, {@code []} for arrays). Types of methods are read from the
 * reflected method when reflection is available. Otherwise, and for {@link #of(Argument)}, they are rendered from
 * Micronaut's compile-time {@link Argument}, which keeps no wildcards ({@code List<? extends Book>} is rendered as
 * {@code java.util.List<Book>}), no raw types ({@code List} as {@code java.util.List<java.lang.Object>}) and no type
  * arguments of array components ({@code List<String>[]} as {@code java.util.List[]}). The first time reflection fails
 * (a {@link LinkageError} or {@link SecurityException}, as in a native image without reflection configuration) is logged
 * at INFO, naming the member, so that a manifest that differs from one rendered with reflection can be explained.
 */
@Internal
public final class JavaTypeNames {

    private static final Logger LOG = LoggerFactory.getLogger(JavaTypeNames.class);
    private static final AtomicBoolean FALLBACK_LOGGED = new AtomicBoolean();

    private JavaTypeNames() {
    }

    /**
     * @param executable a method
     * @return its generic return type, from the reflected method if available, otherwise from its {@link Argument}
     */
    public static String returnType(Executable<?, ?> executable) {
        return reflect(executable)
                .map(it -> it.getGenericReturnType().getTypeName())
                .orElseGet(() -> of(returnArgument(executable)));
    }

    /**
     * @param executable a method
     * @return its generic parameter types, from the reflected method if available, otherwise from its arguments
     */
    public static List<String> parameterTypes(Executable<?, ?> executable) {
        return reflect(executable)
                .map(it -> Arrays.stream(it.getGenericParameterTypes()).map(java.lang.reflect.Type::getTypeName).toList())
                .orElseGet(() -> Arrays.stream(executable.getArguments()).map(JavaTypeNames::of).toList());
    }

    /**
     * @param executable a method of a bean or of a bean introspection
     * @return the reflected method, if reflection can find it (it cannot, for example, in a native image without
     * reflection configuration)
     */
    static Optional<Method> reflect(Executable<?, ?> executable) {
        try {
            if (executable instanceof ExecutableMethod<?, ?> executableMethod) {
                return Optional.of(executableMethod.getTargetMethod());
            }
            if (executable instanceof BeanMethod<?, ?> beanMethod) {
                Class<?>[] parameterTypes = Arrays.stream(beanMethod.getArguments())
                        .map(Argument::getType)
                        .toArray(Class<?>[]::new);
                for (Class<?> type = beanMethod.getDeclaringBean().getBeanType(); type != null;
                     type = type.getSuperclass()) {
                    try {
                        return Optional.of(type.getDeclaredMethod(beanMethod.getName(), parameterTypes));
                    } catch (NoSuchMethodException e) {
                        // look in the superclass
                    }
                }
            }
        } catch (SecurityException | LinkageError e) {
            // no reflection (a native image without reflection configuration): use the compile-time metadata
            fallback(executable.toString(), e);
        }
        return Optional.empty();
    }

    /**
     * Records that a member is rendered from the compile-time metadata because reflection failed: logged once.
     *
     * @param member the member, for the log
     * @param error  why reflection failed
     */
    static void fallback(String member, Throwable error) {
        if (FALLBACK_LOGGED.compareAndSet(false, true)) {
            LOG.info("The binding manifest renders Java types from Micronaut's compile-time metadata, without "
                    + "wildcards, raw types and generic array components, because reflection is not available "
                    + "(first for {}: {}). Further members are not logged.", member, error.toString());
        }
    }

    /**
     * @param executable a method
     * @return its return type as Micronaut's compile-time metadata describes it
     */
    static Argument<?> returnArgument(Executable<?, ?> executable) {
        if (executable instanceof ExecutableMethod<?, ?> executableMethod) {
            return executableMethod.getReturnType().asArgument();
        }
        if (executable instanceof BeanMethod<?, ?> beanMethod) {
            return beanMethod.getReturnType().asArgument();
        }
        throw new IllegalArgumentException("Unsupported executable " + executable.getClass().getName());
    }

    /**
     * @param argument a type with its type arguments
     * @return the type in {@link java.lang.reflect.Type#getTypeName()} form
     */
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
