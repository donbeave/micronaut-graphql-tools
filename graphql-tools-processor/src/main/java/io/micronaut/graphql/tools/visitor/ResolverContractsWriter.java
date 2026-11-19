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
import io.micronaut.inject.ast.ClassElement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes the contracts that a type resolver class inherits into its annotation metadata, as
 * {@code @ResolverContracts}, so that the startup reads them from the bean definition and never looks at the class
 * hierarchy by reflection. A contract is a type of the hierarchy, the class itself included, that declares
 * {@code @GraphQLTypeResolver}; it is recorded with the model class of that annotation, in the order of the binary name of
 * the contract.
 */
final class ResolverContractsWriter {

    private static final String CONTRACTS = "io.micronaut.graphql.tools.binding.internal.meta.ResolverContracts";

    private ResolverContractsWriter() {
    }

    /**
     * @param element a class
     * @return whether the class is a bean that is a type resolver, directly or through a supertype
     */
    static boolean applies(ClassElement element) {
        return element.hasStereotype(AnnotationNames.TYPE_RESOLVER) && ElementTypeModel.isBeanClass(element);
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
        if (contracts.isEmpty()) {
            return;
        }
        List<AnnotationClassValue<?>> models = new ArrayList<>();
        contracts.values().forEach(model -> models.add(new AnnotationClassValue<>(model)));
        element.annotate(CONTRACTS, builder -> builder
                .member("value", models.toArray(new AnnotationClassValue<?>[0]))
                .member("declaredBy", contracts.keySet().toArray(new String[0])));
    }

    @Nullable
    private static String modelClass(AnnotationValue<?> annotation) {
        AnnotationClassValue<?>[] values = annotation.annotationClassValues("value");
        return values.length == 0 ? null : values[0].getName();
    }
}
