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
package io.micronaut.graphql.tools.internal.exceptions.resolver;

import io.micronaut.graphql.tools.internal.exceptions.AbstractMappingException;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.mapping.MappingContext;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Alexey Zhokhov
 */
@Internal
public final class MethodNotFoundException extends AbstractMappingException {

    private final String methodName;
    private final String reason;

    private MethodNotFoundException(String message, MappingContext mappingContext, String methodName) {
        super(BindingCode.FIELD_NOT_BOUND, message, mappingContext);
        this.methodName = methodName;
        this.reason = message;
    }

    /**
     * @param methodName     the name of the method that no root resolver binds
     * @param mappingContext the mapping context of the field
     * @param resolvers      the root resolver beans
     * @return a new exception
     */
    public static MethodNotFoundException forRoot(String methodName, MappingContext mappingContext,
                                                  List<Class<?>> resolvers) {
        return forRoot(methodName, mappingContext, resolvers, null, List.of());
    }

    /**
     * @param methodName    the name of the method that no root resolver binds
     * @param mappingContext the mapping context of the field
     * @param resolvers     the root resolver beans
     * @param coordinate    the coordinate of the field, for the fix in the message
     * @param unannotated   the methods of the root resolvers that have the name and no coordinate, while their bean
     *                      declares coordinates on other methods; empty when there are none
     * @return a new exception
     */
    public static MethodNotFoundException forRoot(String methodName, MappingContext mappingContext,
                                                  List<Class<?>> resolvers, String coordinate,
                                                  List<String> unannotated) {
        String hint = unannotated.isEmpty() || coordinate == null ? "" : " " + String.join(", ", unannotated)
                + (unannotated.size() == 1 ? " exists" : " exist") + " without @GraphQLSchemaCoordinate, but the bean "
                + "declares coordinates on other methods, and only a method with its own coordinate binds a field. "
                + "Fix: annotate it with @GraphQLSchemaCoordinate(\"" + coordinate + "\"), or remove it.";
        return new MethodNotFoundException(
                String.format(
                        "The method `%s` not found in any root resolvers: %s.%s",
                        methodName,
                        "[" + resolvers.stream()
                                .map(Class::getName)
                                .collect(Collectors.joining(", ")) + "]",
                        hint
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
     * explaining that getters only an implementation class declares are not candidates, naming the implementations
     * that declare the property and the fix.
     *
     * @param contractClass   the interface whose introspection is read
     * @param implementations the implementation classes that declare the missing property, may be empty
     * @return a new exception with the same mapping context
     */
    @Internal
    public MethodNotFoundException forContract(Class<?> contractClass, List<Class<?>> implementations) {
        String declaredBy = implementations.isEmpty() ? "" : " `" + methodName + "` is declared by "
                + implementations.stream().map(Class::getName).collect(Collectors.joining(", ")) + ".";
        return new MethodNotFoundException(
                reason + " Properties are read from the interface " + contractClass.getName()
                        + ", which has an introspection of its own, so a getter that only an implementation class "
                        + "declares is not a property." + declaredBy
                        + " Fix: declare the getter on " + contractClass.getName()
                        + ", or remove the interface's own introspection (the @GraphQLType or @Introspected "
                        + "annotation on it).",
                getMappingContext(),
                methodName
        );
    }

    /**
     * The same error for a field of an {@code extend type}, which nothing binds: adds that a customizer may claim it.
     *
     * @return a new exception with the same mapping context
     */
    @Internal
    public MethodNotFoundException forExtensionField() {
        return new MethodNotFoundException(
                reason + " If a GraphQLRuntimeWiringCustomizer serves the field with a data fetcher of its own, return "
                        + "its coordinate from GraphQLRuntimeWiringCustomizer#getClaimedFields().",
                getMappingContext(),
                methodName
        );
    }

    public String getMethodName() {
        return methodName;
    }

}
