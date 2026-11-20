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
package io.micronaut.graphql.tools.visitor;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames;
import io.micronaut.graphql.tools.binding.internal.model.ResolverMarkers;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads what the marker interfaces ({@code QueryResolver}, {@code MutationResolver}, {@code SubscriptionResolver} and
 * {@code TypeResolver<T>}) fix for a class, from the hierarchy of the class element. The result is what the visitor
 * checks against and what it writes into the annotation metadata for the startup, so both read one answer.
 */
final class MarkerAnalysis {

    private static final Map<String, String> OPERATIONS = Map.of(
            AnnotationNames.QUERY_RESOLVER, "query",
            AnnotationNames.MUTATION_RESOLVER, "mutation",
            AnnotationNames.SUBSCRIPTION_RESOLVER, "subscription");

    private static final Set<String> MARKERS = Set.of(AnnotationNames.QUERY_RESOLVER, AnnotationNames.MUTATION_RESOLVER,
            AnnotationNames.SUBSCRIPTION_RESOLVER, AnnotationNames.TYPE_RESOLVER_MARKER);

    private MarkerAnalysis() {
    }

    /**
     * @param element a class, or an introduction advice interface
     * @return what the markers fix for it
     */
    static ResolverMarkers of(ClassElement element) {
        Set<String> operations = new TreeSet<>();
        List<ClassElement> contracts = new ArrayList<>();
        Deque<ClassElement> types = new ArrayDeque<>(List.of(element));
        Set<String> visited = new HashSet<>();
        while (!types.isEmpty()) {
            ClassElement type = types.poll();
            if (!visited.add(type.getName()) || type.getName().equals("java.lang.Object")) {
                continue;
            }
            String operation = OPERATIONS.get(type.getName());
            if (operation != null) {
                operations.add(operation);
            }
            if (type.isInterface() && !MARKERS.contains(type.getName()) && markerBelow(type)) {
                contracts.add(type);
            }
            type.getSuperType().ifPresent(types::add);
            types.addAll(type.getInterfaces());
        }
        return new ResolverMarkers(List.copyOf(operations), sourceType(element), contractMethods(element, contracts));
    }

    private static boolean markerBelow(ClassElement type) {
        return MARKERS.stream().anyMatch(type::isAssignable);
    }

    @Nullable
    private static String sourceType(ClassElement element) {
        if (!element.isAssignable(AnnotationNames.TYPE_RESOLVER_MARKER)
                && !element.getName().equals(AnnotationNames.TYPE_RESOLVER_MARKER)) {
            return null;
        }
        // the type arguments that the class gives to the marker, as the compiler resolves them through the contract interfaces
        for (ClassElement argument : element.getTypeArguments(AnnotationNames.TYPE_RESOLVER_MARKER).values()) {
            String name = nameOf(argument);
            if (name != null) {
                return name;
            }
        }
        // otherwise the marker as a supertype of the hierarchy, with the arguments it is declared with
        Deque<ClassElement> types = new ArrayDeque<>(List.of(element));
        Set<String> visited = new HashSet<>();
        while (!types.isEmpty()) {
            ClassElement type = types.poll();
            if (!visited.add(type.getName())) {
                continue;
            }
            if (type.getName().equals(AnnotationNames.TYPE_RESOLVER_MARKER)) {
                for (ClassElement argument : type.getBoundGenericTypes()) {
                    String name = nameOf(argument);
                    if (name != null) {
                        return name;
                    }
                }
            }
            type.getSuperType().ifPresent(types::add);
            types.addAll(type.getInterfaces());
        }
        return null;
    }

    @Nullable
    private static String nameOf(ClassElement argument) {
        ClassElement type = argument;
        if (type instanceof GenericPlaceholderElement placeholder) {
            type = placeholder.getResolved().orElse(null);
        }
        return type == null || type.getName().equals("java.lang.Object") ? null : type.getName();
    }

    // the methods of the class that implement a method that a contract interface declares: by name, with the same number of
    // parameters, and the same types where the contract does not leave the type to a type variable
    @Nullable
    private static Set<String> contractMethods(ClassElement element, List<ClassElement> contracts) {
        List<MethodElement> declared = new ArrayList<>();
        for (ClassElement contract : contracts) {
            for (MethodElement method : contract.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared())) {
                if (!method.isStatic() && !method.isPrivate()) {
                    declared.add(method);
                }
            }
        }
        if (declared.isEmpty()) {
            return null;
        }
        Set<String> names = new TreeSet<>();
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance())) {
            if (method.isPublic() && declared.stream().anyMatch(it -> implementsMethod(method, it))) {
                names.add(method.getName());
            }
        }
        return names.isEmpty() ? null : names;
    }

    private static boolean implementsMethod(MethodElement method, MethodElement contract) {
        if (!method.getName().equals(contract.getName()) || method.getParameters().length != contract.getParameters().length) {
            return false;
        }
        for (int i = 0; i < method.getParameters().length; i++) {
            ParameterElement own = method.getParameters()[i];
            ParameterElement declared = contract.getParameters()[i];
            if (!(declared.getType() instanceof GenericPlaceholderElement)
                    && !own.getType().getName().equals(declared.getType().getName())) {
                return false;
            }
        }
        return true;
    }
}
