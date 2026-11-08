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
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
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
    // key: bean definition, value: each type of its class hierarchy that declares @GraphQLTypeResolver, and the value
    private final Map<BeanDefinition<?>, Map<Class<?>, Class<?>>> typeResolverContracts = new HashMap<>();
    // methods of beans that are type resolvers for several classes and whose source parameter is none of them
    private final Map<BeanDefinition<?>, List<ExecutableMethod<Object, ?>>> unassignedTypeResolverMethods =
            new HashMap<>();
    // type resolver methods by bean, until the contracts of the beans are read when the binding starts
    private final Map<BeanDefinition<?>, List<ExecutableMethod<Object, ?>>> pendingTypeResolverMethods =
            new LinkedHashMap<>();
    // beans that are type resolvers only through an inherited annotation, whose contract types reflection cannot read
    private final List<BeanDefinition<?>> unreadableTypeResolverContracts = new ArrayList<>();
    // reads the types of a class hierarchy that declare @GraphQLTypeResolver
    private Function<Class<?>, Map<Class<?>, Class<?>>> typeResolverContractReader =
            GraphQLResolversRegistry::findTypeResolverContracts;
    private final List<SchemaCoordinateMethod> schemaCoordinateMethods = new ArrayList<>();
    // key: bean definition, value: the signatures of its methods that carry @GraphQLSchemaCoordinate
    private final Map<BeanDefinition<?>, Set<String>> schemaCoordinateSignatures = new HashMap<>();

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
        getTypeResolverValue(beanDefinition);

        // the contracts of the bean are read when the binding starts, see resolveTypeResolverMethods()
        pendingTypeResolverMethods.computeIfAbsent(beanDefinition, it -> new ArrayList<>()).add(method);
    }

    private static Class<?> getTypeResolverValue(BeanDefinition<?> beanDefinition) {
        AnnotationClassValue<Class<?>> annotationValue = (AnnotationClassValue<Class<?>>) beanDefinition
                .getValue(GraphQLTypeResolver.class, AnnotationMetadata.VALUE_MEMBER)
                .orElseThrow(() -> new IncorrectAnnotationException(beanDefinition.getBeanType()));

        return annotationValue.getType().get();
    }

    /**
     * Registers the pending type resolver methods for the class each one resolves. The annotation metadata keeps one
     * {@link GraphQLTypeResolver} value even when a bean inherits several (it implements two resolver contracts), so
     * the class hierarchy of the bean is read, by reflection on class annotations, for the types that declare it.
     * With several classes, each method belongs to the class of its first parameter, the source object.
     */
    private void resolveTypeResolverMethods() {
        for (Map.Entry<BeanDefinition<?>, List<ExecutableMethod<Object, ?>>> entry
                : pendingTypeResolverMethods.entrySet()) {
            BeanDefinition<?> beanDefinition = entry.getKey();
            Class<?> annotatedClass = getTypeResolverValue(beanDefinition);

            Map<Class<?>, Class<?>> contracts = typeResolverContractReader.apply(beanDefinition.getBeanType());
            if (contracts.isEmpty()) {
                // reflection shows no class annotations (for example a native image without configuration for them)
                if (!beanDefinition.hasDeclaredAnnotation(GraphQLTypeResolver.class)) {
                    // inherited only: the bean may implement several contracts, and they cannot be told apart
                    unreadableTypeResolverContracts.add(beanDefinition);
                    continue;
                }
                // annotated directly: the metadata value is the only one
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

    /**
     * Replaces how the types that declare {@link GraphQLTypeResolver} are read from a bean class hierarchy (tests).
     *
     * @param typeResolverContractReader the reader
     */
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
        schemaCoordinateSignatures.computeIfAbsent(beanDefinition, it -> new HashSet<>())
                .add(getExecutableMethodFullName(method));
    }

    /**
     * @return the methods that carry {@link GraphQLSchemaCoordinate}, sorted by bean class and method
     */
    List<SchemaCoordinateMethod> getSchemaCoordinateMethods() {
        return schemaCoordinateMethods.stream()
                .sorted(Comparator.comparing(SchemaCoordinateMethod::describe))
                .toList();
    }

    /**
     * A method of a bean on which some methods carry {@link GraphQLSchemaCoordinate} binds only if it carries one too.
     */
    private boolean isCandidate(BeanDefinition<?> beanDefinition, ExecutableMethod<Object, ?> method) {
        Set<String> signatures = schemaCoordinateSignatures.get(beanDefinition);
        return signatures == null || signatures.contains(getExecutableMethodFullName(method));
    }

    private List<BeanDefinitionAndMethod> findMethods(BeanDefinitionAndMethods item, String methodName) {
        return item.getExecutableMethods().stream()
                .filter(executableMethod -> executableMethod.getMethodName().equals(methodName))
                .filter(executableMethod -> isCandidate(item.getBeanDefinition(), executableMethod))
                .map(executableMethod -> new BeanDefinitionAndMethod(item.getBeanDefinition(), executableMethod))
                .collect(Collectors.toList());
    }

    /**
     * @param beanType a bean class
     * @return each type of the class hierarchy that declares {@link GraphQLTypeResolver}, and its value, by type name
     */
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

    /**
     * Fails when the contract types of a bean that is a type resolver only through an inherited annotation cannot be
     * read, and when a bean is a type resolver for several classes (it inherits {@link GraphQLTypeResolver} from several
     * contracts) and the source parameter of a method that could bind is none of them.
     */
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

    /**
     * @param beanDefinition a bean definition
     * @return the user's bean class, not the AOP proxy
     */
    static Class<?> getUserBeanType(BeanDefinition<?> beanDefinition) {
        Class<?> beanType = beanDefinition.getBeanType();
        if (Intercepted.class.isAssignableFrom(beanType) && beanType.getSuperclass() != null) {
            return beanType.getSuperclass();
        }
        return beanType;
    }

    boolean hasRootResolvers() {
        return !rootResolvers.isEmpty();
    }

    /**
     * Finds the root resolver methods for a root field by name. When several beans declare a method of that name, the
     * first bean wins, unless one of them is a root resolver only through an inherited annotation (an implemented
     * interface or an extended class): then all candidates are returned, and the caller reports the ambiguity.
     *
     * @param methodName     the field name
     * @param mappingContext the mapping context, for the error
     * @return the candidate methods, never empty
     */
    List<BeanDefinitionAndMethod> getRootExecutableMethod(String methodName, MappingContext mappingContext) {
        List<BeanDefinitionAndMethod> first = null;
        List<BeanDefinitionAndMethod> all = new ArrayList<>();
        boolean inherited = false;

        for (BeanDefinitionAndMethods item : rootResolvers) {
            List<BeanDefinitionAndMethod> result = findMethods(item, methodName);

            if (!result.isEmpty()) {
                if (first == null) {
                    first = result;
                }
                all.addAll(result);
                inherited |= !item.getBeanDefinition().hasDeclaredStereotype(GraphQLRootResolver.class);
            }
        }

        if (first != null) {
            return inherited ? all : first;
        }

        List<Class<?>> resolvers = rootResolvers.stream()
                .map(it -> it.getBeanDefinition().getBeanType())
                .sorted(Comparator.comparing((Function<Class<?>, String>) Class::getName))
                .collect(Collectors.toList());

        throw MethodNotFoundException.forRoot(methodName, mappingContext, resolvers);
    }

    /**
     * @param beanType   the class the type resolvers are declared for
     * @param methodName the field name
     * @return the type resolver methods for the field, possibly none
     */
    List<BeanDefinitionAndMethod> findTypeExecutableMethods(Class<?> beanType, String methodName) {
        List<BeanDefinitionAndMethod> result = new ArrayList<>();

        for (BeanDefinitionAndMethods item : typeResolvers.getOrDefault(beanType, Collections.emptyList())) {
            result.addAll(findMethods(item, methodName));
        }

        return result;
    }

    List<BeanDefinitionAndMethod> getTypeExecutableMethod(Class<?> beanType, String methodName,
                                                          MappingContext mappingContext) {
        resolveTypeResolverMethods();

        List<BeanDefinitionAndMethods> items = typeResolvers.get(beanType);

        List<BeanDefinitionAndMethod> result = findTypeExecutableMethods(beanType, methodName);

        if (!result.isEmpty()) {
            return result;
        }

        List<Class<?>> resolvers = items != null
                ? items.stream()
                .map(it -> it.getBeanDefinition().getBeanType())
                .collect(Collectors.toList())
                : Collections.emptyList();

        throw MethodNotFoundException.forType(methodName, mappingContext, beanType, resolvers);
    }

    /**
     * @param beanDefinition the bean definition
     * @param method         a method of the bean
     * @return the user's bean class (not the AOP proxy) and the method signature
     */
    static String describeMethod(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return getUserBeanType(beanDefinition).getName() + " " + getExecutableMethodFullName(method);
    }

    /**
     * A method that carries {@link GraphQLSchemaCoordinate}.
     *
     * @param beanDefinition the bean the method belongs to
     * @param method         the method
     * @param coordinate     the declared coordinate, as written
     */
    record SchemaCoordinateMethod(BeanDefinition<?> beanDefinition, ExecutableMethod<Object, ?> method,
                                  String coordinate) {

        String describe() {
            return describeMethod(beanDefinition, method);
        }

    }

}
