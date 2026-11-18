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

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.graphql.tools.binding.internal.meta.ResolverContracts;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Reads, from the bean definition of a compiled class, what ContractsGateVisitor wrote. It is loaded by the class loader of
 * the compiled classes (see ContractsGateSpec) and answers in JDK types.
 */
public final class ContractsGateReader {

    private ContractsGateReader() {
    }

    public static Map<String, List<String>> read(String type) throws ClassNotFoundException {
        try (ApplicationContext context = ApplicationContext.builder(Map.of("micronaut.graphql.tools.eager-init", false)).start()) {
            AnnotationMetadata metadata = context.getBeanDefinition(Class.forName(type, true,
                    Thread.currentThread().getContextClassLoader())).getAnnotationMetadata();
            return Map.of(
                    "value", Arrays.stream(metadata.classValues(ResolverContracts.class, "value")).map(Class::getName).toList(),
                    "declaredBy", Arrays.asList(metadata.stringValues(ResolverContracts.class, "declaredBy")));
        }
    }
}
