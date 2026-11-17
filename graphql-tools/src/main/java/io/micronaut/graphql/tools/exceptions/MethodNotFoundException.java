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
package io.micronaut.graphql.tools.exceptions;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.MappingContext;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Alexey Zhokhov
 */
public final class MethodNotFoundException extends AbstractMappingException {

    private final String methodName;
    private final String reason;

    private MethodNotFoundException(String message, MappingContext mappingContext, String methodName) {
        super(message, mappingContext);
        this.methodName = methodName;
        this.reason = message;
    }

    public static MethodNotFoundException forRoot(String methodName, MappingContext mappingContext,
                                                  List<Class<?>> resolvers) {
        return new MethodNotFoundException(
                String.format(
                        "The method `%s` not found in any root resolvers: %s.",
                        methodName,
                        "[" + resolvers.stream()
                                .map(Class::getName)
                                .collect(Collectors.joining(", ")) + "]"
                ),
                mappingContext,
                methodName
        );
    }

    public static MethodNotFoundException forType(String methodName, MappingContext mappingContext,
                                                  Class<?> objectTypeClass, List<Class<?>> resolvers) {
        return new MethodNotFoundException(
                String.format(
                        resolvers.isEmpty()
                                ? "The property or method `%s` not found in %s's type."
                                : "The property or method `%s` not found in %s's type or it resolvers: %s.",
                        methodName,
                        objectTypeClass.getName(),
                        "[" + resolvers.stream()
                                .map(Class::getName)
                                .collect(Collectors.joining(", ")) + "]"
                ),
                mappingContext,
                methodName
        );
    }

    public static MethodNotFoundException forInput(String methodName, MappingContext mappingContext,
                                                   Class<?> objectTypeClass) {
        return new MethodNotFoundException(
                String.format(
                        "The property or method `%s` not found in %s's input.",
                        methodName,
                        objectTypeClass.getName()
                ),
                mappingContext,
                methodName
        );
    }

    /**
     * The same error for a GraphQL type whose properties are read through the bean introspection of a Java interface,
     * explaining that getters only an implementation class declares are not candidates.
     *
     * @param contractClass the interface whose introspection is read
     * @return a new exception with the same mapping context
     */
    @Internal
    public MethodNotFoundException forContract(Class<?> contractClass) {
        return new MethodNotFoundException(
                reason + " Properties are read from the interface " + contractClass.getName()
                        + ", so a getter that only an implementation class declares is not a property.",
                getMappingContext(),
                methodName
        );
    }

    public String getMethodName() {
        return methodName;
    }

}
