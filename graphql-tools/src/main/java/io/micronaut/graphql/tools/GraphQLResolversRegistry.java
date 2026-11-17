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

import io.micronaut.aop.Intercepted;
import io.micronaut.context.annotation.Infrastructure;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.exceptions.AmbiguousTypeResolverException;
import io.micronaut.graphql.tools.exceptions.IncorrectAnnotationException;
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException;
import io.micronaut.graphql.tools.exceptions.TypeResolverContractsNotFoundException;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.micronaut.graphql.tools.MicronautUtils.getExecutableMethodFullName;

/**
 * @author Alexey Zhokhov
 */
@Internal
@Singleton
@Infrastructure
public final class GraphQLResolversRegistry {

    private final List<BeanDefinitionAndMethods> rootResolvers = new ArrayList<>();
    private final Map<Class<?>, List<BeanDefinitionAndMethods>> typeResolvers = new HashMap<>();
    private final Map<BeanDefinition<?>, Map<Class<?>, Class<?>>> typeResolverContracts = new HashMap<>();
    private final Map<BeanDefinition<?>, List<ExecutableMethod<Object, ?>>> unassignedTypeResolverMethods =
            new HashMap<>();
    private final Map<BeanDefinition<?>, List<ExecutableMethod<Object, ?>>> pendingTypeResolverMethods =
            new LinkedHashMap<>();
    private final List<BeanDefinition<?>> unreadableTypeResolverContracts = new ArrayList<>();
    private Function<Class<?>, Map<Class<?>, Class<?>>> typeResolverContractReader =
            GraphQLResolversRegistry::findTypeResolverContracts;
    private final List<SchemaCoordinateMethod> schemaCoordinateMethods = new ArrayList<>();
    private final Map<BeanDefinition<?>, Map<String, String>> schemaCoordinateSignatures = new HashMap<>();

    public void registerRootResolverExecutableMethod(BeanDefinition<?> beanDefinition, ExecutableMethod<Object, ?> method) {
        if (method.isAbstract()) {
            return;
        }
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
        if (method.isAbstract()) {
            return;
        }
        getTypeResolverValue(beanDefinition);

        pendingTypeResolverMethods.computeIfAbsent(beanDefinition, it -> new ArrayList<>()).add(method);
    }

    private static Class<?> getTypeResolverValue(BeanDefinition<?> beanDefinition) {
        AnnotationClassValue<Class<?>> annotationValue = (AnnotationClassValue<Class<?>>) beanDefinition
                .getValue(GraphQLTypeResolver.class, AnnotationMetadata.VALUE_MEMBER)
                .orElseThrow(() -> new IncorrectAnnotationException(beanDefinition.getBeanType()));

        return annotationValue.getType().get();
    }

    private void resolveTypeResolverMethods() {
        for (Map.Entry<BeanDefinition<?>, List<ExecutableMethod<Object, ?>>> entry
                : pendingTypeResolverMethods.entrySet()) {
            BeanDefinition<?> beanDefinition = entry.getKey();
            Class<?> annotatedClass = getTypeResolverValue(beanDefinition);

            Map<Class<?>, Class<?>> contracts = typeResolverContractReader.apply(beanDefinition.getBeanType());
            if (contracts.isEmpty()) {
                // reflection shows no class annotations (for example a native image without configuration for them)
                if (!beanDefinition.hasDeclaredAnnotation(GraphQLTypeResolver.class)) {
                    unreadableTypeResolverContracts.add(beanDefinition);
                    continue;
                }
                contracts = Map.of(getUserBeanType(beanDefinition), annotatedClass);
            }
            typeResolverContracts.put(beanDefinition, contracts);

            Set<Class<?>> modelClasses = new HashSet<>(contracts.values());
            for (ExecutableMethod<Object, ?> method : entry.getValue()) {
                Class<?> modelClass = annotatedClass;
                if (modelClasses.size() > 1) {
                    Class<?>[] argumentTypes = method.getArgumentTypes();
                    if (argumentTypes.length == 0 || !modelClasses.contains(argumentTypes[0])) {
                        unassignedTypeResolverMethods.computeIfAbsent(beanDefinition, it -> new ArrayList<>())
                                .add(method);
                        continue;
                    }
                    modelClass = argumentTypes[0];
                }
                addTypeResolverMethod(modelClass, beanDefinition, method);
            }
        }

        pendingTypeResolverMethods.clear();
    }

    private void addTypeResolverMethod(Class<?> modelClass, BeanDefinition<?> beanDefinition,
                                       ExecutableMethod<Object, ?> method) {
        typeResolvers.computeIfAbsent(modelClass, it -> new ArrayList<>()).stream()
                .filter(it -> it.getBeanDefinition().equals(beanDefinition))
                .findFirst()
                .orElseGet(() -> {
                    BeanDefinitionAndMethods item = new BeanDefinitionAndMethods(beanDefinition);
                    typeResolvers.get(modelClass).add(item);
                    return item;
                })
                .addExecutableMethod(method);
    }

    void setTypeResolverContractReader(Function<Class<?>, Map<Class<?>, Class<?>>> typeResolverContractReader) {
        this.typeResolverContractReader = typeResolverContractReader;
    }

    /**
     * Records a method that carries {@link GraphQLSchemaCoordinate}, whatever the bean is.
     *
     * @param beanDefinition the bean definition
     * @param method         the method
     */
    public void registerSchemaCoordinateExecutableMethod(BeanDefinition<?> beanDefinition,
                                                         ExecutableMethod<Object, ?> method) {
        String coordinate = method.stringValue(GraphQLSchemaCoordinate.class).orElse("");

        schemaCoordinateMethods.add(new SchemaCoordinateMethod(beanDefinition, method, coordinate));
        schemaCoordinateSignatures.computeIfAbsent(beanDefinition, it -> new HashMap<>())
                .put(getExecutableMethodFullName(method), coordinate);
    }

    List<SchemaCoordinateMethod> getSchemaCoordinateMethods() {
        return schemaCoordinateMethods.stream()
                .sorted(Comparator.comparing(SchemaCoordinateMethod::describe))
                .toList();
    }

    private boolean isCandidate(BeanDefinition<?> beanDefinition, ExecutableMethod<Object, ?> method) {
        Map<String, String> signatures = schemaCoordinateSignatures.get(beanDefinition);
        return signatures == null || signatures.containsKey(getExecutableMethodFullName(method));
    }

    private List<BeanDefinitionAndMethod> findMethods(BeanDefinitionAndMethods item, String methodName,
                                                      Set<String> coordinates) {
        Map<String, String> signatures = schemaCoordinateSignatures.get(item.getBeanDefinition());
        return item.getExecutableMethods().stream()
                .filter(executableMethod -> executableMethod.getMethodName().equals(methodName))
                .filter(executableMethod -> signatures == null
                        || signatures.containsKey(getExecutableMethodFullName(executableMethod))
                        && coordinates.contains(signatures.get(getExecutableMethodFullName(executableMethod))))
                .map(executableMethod -> new BeanDefinitionAndMethod(item.getBeanDefinition(), executableMethod))
                .collect(Collectors.toList());
    }

    private static Map<Class<?>, Class<?>> findTypeResolverContracts(Class<?> beanType) {
        Map<Class<?>, Class<?>> contracts = new TreeMap<>(Comparator.comparing(Class::getName));
        Deque<Class<?>> types = new ArrayDeque<>(List.of(beanType));
        Set<Class<?>> visited = new HashSet<>();

        while (!types.isEmpty()) {
            Class<?> type = types.poll();
            if (!visited.add(type)) {
                continue;
            }
            GraphQLTypeResolver annotation = type.getDeclaredAnnotation(GraphQLTypeResolver.class);
            if (annotation != null) {
                contracts.put(type, annotation.value());
            }
            if (type.getSuperclass() != null && type.getSuperclass() != Object.class) {
                types.add(type.getSuperclass());
            }
            types.addAll(Arrays.asList(type.getInterfaces()));
        }

        return contracts;
    }

    void verifyTypeResolverContracts() {
        resolveTypeResolverMethods();

        unreadableTypeResolverContracts.stream()
                .map(GraphQLResolversRegistry::getUserBeanType)
                .min(Comparator.comparing(Class::getName))
                .ifPresent(beanType -> {
                    throw new TypeResolverContractsNotFoundException(beanType);
                });

        unassignedTypeResolverMethods.entrySet().stream()
                .sorted(Comparator.comparing(it -> it.getKey().getBeanType().getName()))
                .forEach(entry -> {
                    List<String> methods = entry.getValue().stream()
                            .filter(method -> isCandidate(entry.getKey(), method))
                            .map(MicronautUtils::getExecutableMethodFullName)
                            .sorted()
                            .toList();
                    if (!methods.isEmpty()) {
                        throw new AmbiguousTypeResolverException(getUserBeanType(entry.getKey()),
                                typeResolverContracts.get(entry.getKey()), methods);
                    }
                });
    }

    static Class<?> getUserBeanType(BeanDefinition<?> beanDefinition) {
        Class<?> beanType = beanDefinition.getBeanType();
        if (Intercepted.class.isAssignableFrom(beanType) && beanType.getSuperclass() != null) {
            return beanType.getSuperclass();
        }
        return beanType;
    }

    List<BeanDefinitionAndMethod> getResolverMethods() {
        List<BeanDefinitionAndMethods> items = new ArrayList<>(rootResolvers);
        typeResolvers.values().forEach(items::addAll);

        List<BeanDefinitionAndMethod> result = new ArrayList<>();
        for (BeanDefinitionAndMethods item : items) {
            for (ExecutableMethod<Object, ?> method : item.getExecutableMethods()) {
                if (isCandidate(item.getBeanDefinition(), method)) {
                    result.add(new BeanDefinitionAndMethod(item.getBeanDefinition(), method));
                }
            }
        }
        return result;
    }

    boolean hasRootResolvers() {
        return !rootResolvers.isEmpty();
    }

    List<BeanDefinitionAndMethod> getRootExecutableMethods(String operationTypeName, String methodName,
                                                           MappingContext mappingContext) {
        List<BeanDefinitionAndMethod> all = new ArrayList<>();

        for (BeanDefinitionAndMethods item : rootResolvers) {
            all.addAll(findMethods(item, methodName, Set.of(operationTypeName + "." + methodName)));
        }

        if (!all.isEmpty()) {
            return all;
        }

        List<Class<?>> resolvers = rootResolvers.stream()
                .map(it -> it.getBeanDefinition().getBeanType())
                .sorted(Comparator.comparing((Function<Class<?>, String>) Class::getName))
                .collect(Collectors.toList());

        throw MethodNotFoundException.forRoot(methodName, mappingContext, resolvers);
    }

    List<BeanDefinitionAndMethod> findTypeExecutableMethods(Class<?> beanType, String typeName, String methodName) {
        List<BeanDefinitionAndMethod> result = new ArrayList<>();

        for (BeanDefinitionAndMethods item : typeResolvers.getOrDefault(beanType, Collections.emptyList())) {
            result.addAll(findMethods(item, methodName, Set.of(typeName + "." + methodName)));
        }

        return result;
    }

    MethodNotFoundException typeMethodNotFound(Class<?> beanType, String methodName, MappingContext mappingContext) {
        List<BeanDefinitionAndMethods> items = typeResolvers.get(beanType);

        List<Class<?>> resolvers = items != null
                ? items.stream()
                .map(it -> it.getBeanDefinition().getBeanType())
                .collect(Collectors.toList())
                : Collections.emptyList();

        return MethodNotFoundException.forType(methodName, mappingContext, beanType, resolvers);
    }

    static String describeMethod(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return getUserBeanType(beanDefinition).getName() + " " + getExecutableMethodFullName(method);
    }

    record SchemaCoordinateMethod(BeanDefinition<?> beanDefinition, ExecutableMethod<Object, ?> method,
                                  String coordinate) {

        String describe() {
            return describeMethod(beanDefinition, method);
        }

    }

}
