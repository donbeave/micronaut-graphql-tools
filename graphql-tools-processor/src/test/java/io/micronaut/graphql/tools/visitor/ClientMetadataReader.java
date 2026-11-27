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
import io.micronaut.graphql.tools.client.internal.GraphQLOperationMetadata;
import io.micronaut.inject.ExecutableMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads, from the bean definition of a compiled client interface, what the client visitor wrote on each operation method. It is
 * loaded by the class loader of the compiled classes (see ClientVisitorSpec) and answers in JDK types.
 */
public final class ClientMetadataReader {

    private ClientMetadataReader() {
    }

    /**
     * @param type the client interface
     * @return for each method that carries the metadata, by name: document, operationName, operationType, variables, payload
     */
    public static Map<String, Map<String, Object>> read(String type) throws ClassNotFoundException {
        try (ApplicationContext context = ApplicationContext.builder(Map.of("micronaut.graphql.tools.eager-init", false)).start()) {
            Class<?> client = Class.forName(type, true, Thread.currentThread().getContextClassLoader());
            Map<String, Map<String, Object>> found = new LinkedHashMap<>();
            for (ExecutableMethod<?, ?> method : context.getBeanDefinition(client).getExecutableMethods()) {
                AnnotationMetadata metadata = method.getAnnotationMetadata();
                if (metadata.hasAnnotation(GraphQLOperationMetadata.class)) {
                    Map<String, Object> values = new LinkedHashMap<>();
                    values.put("document", metadata.stringValue(GraphQLOperationMetadata.class, "document").orElse(null));
                    values.put("operationName", metadata.stringValue(GraphQLOperationMetadata.class, "operationName").orElse(null));
                    values.put("operationType", metadata.stringValue(GraphQLOperationMetadata.class, "operationType").orElse(null));
                    values.put("variables", new ArrayList<>(Arrays.asList(metadata.stringValues(GraphQLOperationMetadata.class, "variables"))));
                    values.put("payload", metadata.stringValue(GraphQLOperationMetadata.class, "payload").orElse(null));
                    found.put(method.getMethodName(), values);
                }
            }
            return found;
        }
    }

    /**
     * @param type the client interface
     * @return the names of the methods of the bean definition that carry no metadata
     */
    public static List<String> withoutMetadata(String type) throws ClassNotFoundException {
        try (ApplicationContext context = ApplicationContext.builder(Map.of("micronaut.graphql.tools.eager-init", false)).start()) {
            Class<?> client = Class.forName(type, true, Thread.currentThread().getContextClassLoader());
            List<String> names = new ArrayList<>();
            for (ExecutableMethod<?, ?> method : context.getBeanDefinition(client).getExecutableMethods()) {
                if (!method.getAnnotationMetadata().hasAnnotation(GraphQLOperationMetadata.class)) {
                    names.add(method.getMethodName());
                }
            }
            return names;
        }
    }
}
