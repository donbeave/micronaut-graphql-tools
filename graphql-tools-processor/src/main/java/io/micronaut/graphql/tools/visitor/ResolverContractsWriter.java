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

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames;
import io.micronaut.graphql.tools.binding.internal.model.ResolverMarkers;
import io.micronaut.inject.ast.ClassElement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes what a resolver class inherits into its annotation metadata, as {@code @ResolverContracts}: the contracts of a
 * type resolver, the root operations of its marker interfaces and the methods of its contract interfaces, each with the operations and
 * the source type of its contract. The
 * startup reads them from the bean definition and never looks at the class hierarchy by reflection. A contract is a type of the hierarchy, the class itself included, that declares
 * {@code @GraphQLTypeResolver}; it is recorded with the model class of that annotation, in the order of the binary name of
 * the contract.
 */
final class ResolverContractsWriter {

    private static final String CONTRACTS = "io.micronaut.graphql.tools.binding.internal.meta.ResolverContracts";

    private ResolverContractsWriter() {
    }

    /**
     * @param element a class
     * @return whether the class is a resolver, directly or through a supertype, that can be a bean: a bean class, or a
     * concrete class that a factory method can expose, which carries no scope annotation of its own
     */
    static boolean applies(ClassElement element) {
        return (element.hasStereotype(AnnotationNames.TYPE_RESOLVER) || element.hasStereotype(AnnotationNames.ROOT_RESOLVER))
                && (ElementTypeModel.isBeanClass(element) || ElementTypeModel.isConcreteClass(element));
    }

    /**
     * Writes the contracts of the class, when it has any.
     *
     * @param element a class for which {@link #applies} holds
     */
    static void write(ClassElement element) {
        // contract binary name -> model class name
        TreeMap<String, String> contracts = new TreeMap<>();
        Deque<ClassElement> types = new ArrayDeque<>(List.of(element));
        Set<String> visited = new HashSet<>();
        while (!types.isEmpty()) {
            ClassElement type = types.poll();
            if (!visited.add(type.getName()) || type.getName().equals("java.lang.Object")) {
                continue;
            }
            type.findDeclaredAnnotation(AnnotationNames.TYPE_RESOLVER).ifPresent(annotation -> {
                String model = modelClass(annotation);
                if (model != null) {
                    contracts.put(type.getName(), model);
                }
            });
            type.getSuperType().ifPresent(types::add);
            types.addAll(type.getInterfaces());
        }
        // the marker TypeResolver<T> names its source type by its type argument
        ResolverMarkers markers = MarkerAnalysis.of(element);
        if (markers.sourceType() != null) {
            contracts.put(AnnotationNames.TYPE_RESOLVER_MARKER, markers.sourceType());
        }
        List<String> methods = markers.contractMethods() == null ? List.of()
                : markers.contractMethods().stream().map(ResolverMarkers.ContractMethod::encode).toList();
        if (contracts.isEmpty() && markers.operations().isEmpty() && methods.isEmpty()) {
            return;
        }
        List<AnnotationClassValue<?>> models = new ArrayList<>();
        contracts.values().forEach(model -> models.add(new AnnotationClassValue<>(model)));
        element.annotate(CONTRACTS, builder -> builder
                .member("value", models.toArray(new AnnotationClassValue<?>[0]))
                .member("declaredBy", contracts.keySet().toArray(new String[0]))
                .member("operations", markers.operations().toArray(new String[0]))
                .member("contractMethods", methods.toArray(new String[0])));
    }

    @Nullable
    private static String modelClass(AnnotationValue<?> annotation) {
        AnnotationClassValue<?>[] values = annotation.annotationClassValues("value");
        // the annotation on the marker TypeResolver has no source type: its value is the default, void
        return values.length == 0 || values[0].getName().equals("void") ? null : values[0].getName();
    }
}
