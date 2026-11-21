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
import java.util.TreeMap;
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

    private static final String CONTINUATION = "kotlin.coroutines.Continuation";

    private MarkerAnalysis() {
    }

    /**
     * @param element a class, or an introduction advice interface
     * @return what the markers fix for it
     */
    static ResolverMarkers of(ClassElement element) {
        Set<String> operations = new TreeSet<>();
        List<ClassElement> contracts = new ArrayList<>();
        List<ClassElement> annotated = new ArrayList<>();
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
            } else if (type.isInterface() && !MARKERS.contains(type.getName()) && annotatedContract(type)) {
                annotated.add(type);
            }
            type.getSuperType().ifPresent(types::add);
            types.addAll(type.getInterfaces());
        }
        String sourceType = sourceType(element);
        return new ResolverMarkers(List.copyOf(operations), sourceType,
                contractMethods(contracts, contracts.isEmpty() ? List.of() : annotated, sourceType));
    }

    /**
     * @param method a method of a class or of a contract interface
     * @return its number of parameters, without the continuation of a Kotlin suspend function
     */
    static int arity(MethodElement method) {
        ParameterElement[] parameters = method.getParameters();
        int count = parameters.length;
        if (count > 0 && method.isSuspend() && CONTINUATION.equals(parameters[count - 1].getType().getName())) {
            count--;
        }
        return count;
    }

    // a contract interface that carries @GraphQLRootResolver or @GraphQLTypeResolver itself: a class that also implements a
    // marker contract keeps binding its methods
    private static boolean annotatedContract(ClassElement type) {
        return type.hasDeclaredStereotype(AnnotationNames.ROOT_RESOLVER) || type.hasDeclaredStereotype(AnnotationNames.TYPE_RESOLVER);
    }

    private static boolean markerBelow(ClassElement type) {
        return MARKERS.stream().anyMatch(type::isAssignable);
    }

    // The type argument of the marker TypeResolver<T> as the hierarchy declares it. The class element itself is asked for its
    // supertypes only: a question that the compiler answers for the class (isAssignable, getTypeArguments) before the visitor
    // annotates the class makes the annotation miss the bean definition under javac and KSP.
    @Nullable
    private static String sourceType(ClassElement element) {
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
            if (type != element && !type.getName().equals(AnnotationNames.TYPE_RESOLVER_MARKER)
                    && type.isAssignable(AnnotationNames.TYPE_RESOLVER_MARKER)) {
                // a generic supertype that gives its own type argument to the marker: Base<T> implements TypeResolver<T>
                for (ClassElement argument : type.getTypeArguments(AnnotationNames.TYPE_RESOLVER_MARKER).values()) {
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

    // the methods that the contract interfaces declare or inherit from the interfaces they extend, one entry per method of each
    // contract with what that contract fixes: the class implements each of them, so these are the methods of the class that
    // are contract methods. The methods of the class itself are not listed here: asking the class element for its methods
    // before the visitor annotates it makes the annotation not reach the bean definition.
    @Nullable
    private static List<ResolverMarkers.ContractMethod> contractMethods(List<ClassElement> markerContracts,
                                                                       List<ClassElement> annotatedContracts,
                                                                       @Nullable String sourceType) {
        Map<String, ResolverMarkers.ContractMethod> entries = new TreeMap<>();
        for (ClassElement contract : markerContracts) {
            List<String> operations = OPERATIONS.entrySet().stream().filter(it -> contract.isAssignable(it.getKey()))
                    .map(Map.Entry::getValue).sorted().toList();
            String model = contract.isAssignable(AnnotationNames.TYPE_RESOLVER_MARKER) ? sourceType : null;
            add(entries, contract, operations, model);
        }
        for (ClassElement contract : annotatedContracts) {
            add(entries, contract, List.of(), declaredModel(contract));
        }
        return entries.isEmpty() ? null : List.copyOf(entries.values());
    }

    private static void add(Map<String, ResolverMarkers.ContractMethod> entries, ClassElement contract,
                            List<String> operations, @Nullable String model) {
        for (MethodElement method : contract.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance())) {
            if (!method.isStatic() && !method.isPrivate()) {
                ResolverMarkers.ContractMethod entry =
                        new ResolverMarkers.ContractMethod(operations, model, method.getName(), arity(method));
                entries.putIfAbsent(entry.encode(), entry);
            }
        }
    }

    // the model class that @GraphQLTypeResolver names on a contract interface; null when it names none
    @Nullable
    private static String declaredModel(ClassElement contract) {
        return contract.findDeclaredAnnotation(AnnotationNames.TYPE_RESOLVER)
                .map(it -> it.annotationClassValues("value"))
                .filter(it -> it.length > 0 && !it[0].getName().equals("void"))
                .map(it -> it[0].getName())
                .orElse(null);
    }
}
