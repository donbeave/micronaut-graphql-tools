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
package io.micronaut.graphql.tools.internal.model;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.internal.exceptions.ClassNotIntrospectedException;
import io.micronaut.graphql.tools.internal.exceptions.ImplementationNotFoundException;
import io.micronaut.graphql.tools.internal.exceptions.IncorrectImplementationException;
import io.micronaut.graphql.tools.internal.exceptions.MultipleImplementationsFoundException;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.micronaut.graphql.tools.MappingContext;

/** Looks up the bean introspections of the classes that the binding maps. */
@Internal
public final class GraphQLBeanIntrospectionRegistry {

    private final Map<Class<?>, BeanIntrospection<Object>> typeIntrospections = new HashMap<>();

    private final Map<Class<?>, List<Class<?>>> interfaceToImplementation = new HashMap<>();

    private final Map<Class<?>, Class<?>> implementationToInterface = new HashMap<>();

    private boolean typeIntrospectionsLoaded = false;

  public   GraphQLBeanIntrospectionRegistry() {
    }

    private void loadTypeIntrospections() {
        if (typeIntrospectionsLoaded) {
            return;
        }

        typeIntrospections.clear();
        interfaceToImplementation.clear();

        for (BeanIntrospection<Object> introspection : BeanIntrospector.SHARED.findIntrospections(GraphQLType.class)) {
            typeIntrospections.put(introspection.getBeanType(), introspection);

            AnnotationValue<GraphQLType> annotationValue = introspection.getAnnotation(GraphQLType.class);
            Class<?> modelInterface = annotationValue.get(AnnotationMetadata.VALUE_MEMBER, Class.class).get();

            if (modelInterface.isInterface()) {
                interfaceToImplementation.putIfAbsent(modelInterface, new ArrayList<>());
                interfaceToImplementation.get(modelInterface).add(introspection.getBeanType());

                implementationToInterface.put(introspection.getBeanType(), modelInterface);
            }
        }

        typeIntrospectionsLoaded = true;
    }

    public BeanIntrospection<Object> getGraphQlTypeBeanIntrospection(
            MappingContext mappingContext,
            Class<?> clazz
    ) {
        loadTypeIntrospections();

        Class<?> contractClass = clazz.isInterface() ? clazz : implementationToInterface.get(clazz);

        if (contractClass != null) {
            Optional<BeanIntrospection<Object>> contractIntrospection = findContractIntrospection(contractClass);

            if (contractIntrospection.isPresent()) {
                for (Class<?> implementationClass
                        : interfaceToImplementation.getOrDefault(contractClass, List.of())) {
                    if (!contractClass.isAssignableFrom(implementationClass)) {
                        throw new IncorrectImplementationException(mappingContext, contractClass, implementationClass);
                    }
                }

                return contractIntrospection.get();
            }
        }

        if (clazz.isInterface()) {
            if (!interfaceToImplementation.containsKey(clazz)) {
                throw new ImplementationNotFoundException(mappingContext, clazz);
            }

            if (interfaceToImplementation.get(clazz).size() > 1) {
                throw new MultipleImplementationsFoundException(
                        mappingContext,
                        clazz,
                        interfaceToImplementation.get(clazz)
                );
            }

            Class<?> implementationClass = interfaceToImplementation.get(clazz).get(0);

            if (!clazz.isAssignableFrom(implementationClass)) {
                throw new IncorrectImplementationException(mappingContext, clazz, implementationClass);
            }

            return typeIntrospections.get(implementationClass);
        } else {
            if (!typeIntrospections.containsKey(clazz)) {
                return (BeanIntrospection<Object>) BeanIntrospector.SHARED.findIntrospection(clazz).orElseThrow(() -> {
                    throw new ClassNotIntrospectedException(mappingContext, clazz, GraphQLType.class);
                });
            }

            return typeIntrospections.get(clazz);
        }
    }

    private Optional<BeanIntrospection<Object>> findContractIntrospection(Class<?> interfaceClass) {
        BeanIntrospection<Object> introspection = typeIntrospections.get(interfaceClass);

        if (introspection != null) {
            return Optional.of(introspection);
        }

        return BeanIntrospector.SHARED.findIntrospection((Class<Object>) interfaceClass);
    }

    public List<Class<?>> getImplementationClasses(Class<?> interfaceClass) {
        loadTypeIntrospections();

        return interfaceToImplementation.getOrDefault(interfaceClass, List.of());
    }

    public List<Class<?>> getIntrospectedClasses() {
        return BeanIntrospector.SHARED.findIntrospectedTypes(reference -> true).stream()
                .filter(it -> !it.isInterface() && !Modifier.isAbstract(it.getModifiers()))
                .toList();
    }

    public Class getInterfaceClass(Class implementationClass) {
        loadTypeIntrospections();

        return implementationToInterface.getOrDefault(implementationClass, implementationClass);
    }

}
