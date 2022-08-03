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
package io.micronaut.graphql.tools;

import io.micronaut.context.annotation.Infrastructure;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.exceptions.IncorrectAnnotationException;
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Alexey Zhokhov
 */
@Internal
@Singleton
@Infrastructure
public final class GraphQLResolversRegistry {

    private final List<BeanDefinitionAndMethods> rootResolvers = new ArrayList<>();
    private final Map<Class<?>, List<BeanDefinitionAndMethods>> typeResolvers = new HashMap<>();

    public void registerRootResolverExecutableMethod(BeanDefinition<?> beanDefinition, ExecutableMethod<Object, ?> method) {
        rootResolvers.stream()
                .filter(it -> it.getBeanDefinition().equals(beanDefinition))
                .findFirst()
                .orElseGet(() -> {
                    BeanDefinitionAndMethods item = new BeanDefinitionAndMethods(beanDefinition);
                    rootResolvers.add(item);
                    return item;
                })
                .addExecutableMethod(method);
    }

    public void registerTypeResolverExecutableMethod(BeanDefinition<?> beanDefinition, ExecutableMethod<Object, ?> method) {
        AnnotationClassValue<Class<?>> annotationValue = (AnnotationClassValue<Class<?>>) beanDefinition
                .getValue(GraphQLTypeResolver.class, AnnotationMetadata.VALUE_MEMBER)
                .orElseThrow(() -> new IncorrectAnnotationException(beanDefinition.getBeanType()));

        Class<?> modelClass = annotationValue.getType().get();

        typeResolvers.putIfAbsent(modelClass, new ArrayList<>());

        typeResolvers.get(modelClass).stream()
                .filter(it -> it.getBeanDefinition().equals(beanDefinition))
                .findFirst()
                .orElseGet(() -> {
                    BeanDefinitionAndMethods item = new BeanDefinitionAndMethods(beanDefinition);
                    typeResolvers.get(modelClass).add(item);
                    return item;
                })
                .addExecutableMethod(method);
    }

    boolean hasRootResolvers() {
        return !rootResolvers.isEmpty();
    }

    List<BeanDefinitionAndMethod> getRootExecutableMethod(String methodName, MappingContext mappingContext) {
        for (BeanDefinitionAndMethods item : rootResolvers) {
            List<BeanDefinitionAndMethod> result = item.getExecutableMethods().stream()
                    .filter(executableMethod -> executableMethod.getMethodName().equals(methodName))
                    .map(executableMethod -> new BeanDefinitionAndMethod(item.getBeanDefinition(), executableMethod))
                    .collect(Collectors.toList());

            if (!result.isEmpty()) {
                return result;
            }
        }

        List<Class<?>> resolvers = rootResolvers.stream()
                .map(it -> it.getBeanDefinition().getBeanType())
                .sorted(Comparator.comparing((Function<Class<?>, String>) Class::getName))
                .collect(Collectors.toList());

        throw MethodNotFoundException.forRoot(methodName, mappingContext, resolvers);
    }

    List<BeanDefinitionAndMethod> getTypeExecutableMethod(Class<?> beanType, String methodName,
                                                          MappingContext mappingContext) {
        List<BeanDefinitionAndMethods> items = typeResolvers.get(beanType);

        if (items != null) {
            List<BeanDefinitionAndMethod> result = new ArrayList<>();

            for (BeanDefinitionAndMethods item : items) {
                result.addAll(
                        item.getExecutableMethods().stream()
                                .filter(executableMethod -> executableMethod.getMethodName().equals(methodName))
                                .map(executableMethod -> new BeanDefinitionAndMethod(item.getBeanDefinition(), executableMethod))
                                .collect(Collectors.toList())
                );
            }

            if (!result.isEmpty()) {
                return result;
            }
        }

        List<Class<?>> resolvers = items != null
                ? items.stream()
                .map(it -> it.getBeanDefinition().getBeanType())
                .collect(Collectors.toList())
                : Collections.emptyList();

        throw MethodNotFoundException.forType(methodName, mappingContext, beanType, resolvers);
    }

}
