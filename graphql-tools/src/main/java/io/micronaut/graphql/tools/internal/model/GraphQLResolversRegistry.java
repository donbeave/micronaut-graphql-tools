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

import io.micronaut.aop.Intercepted;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Infrastructure;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.mapping.MappingContext;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.binding.internal.meta.ResolverContracts;
import io.micronaut.graphql.tools.binding.internal.model.JMethod;
import io.micronaut.graphql.tools.binding.internal.model.ResolverMarkers;
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules;
import io.micronaut.graphql.tools.internal.MicronautUtils;
import io.micronaut.graphql.tools.internal.exceptions.implementation.AmbiguousTypeResolverException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.IncorrectAnnotationException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.MethodNotFoundException;
import io.micronaut.graphql.tools.internal.exceptions.implementation.TypeResolverContractsNotFoundException;
import io.micronaut.graphql.tools.resolver.MutationResolver;
import io.micronaut.graphql.tools.resolver.QueryResolver;
import io.micronaut.graphql.tools.resolver.SubscriptionResolver;
import io.micronaut.graphql.tools.resolver.TypeResolver;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.micronaut.graphql.tools.internal.MicronautUtils.getExecutableMethodFullName;

/**
 * @author Alexey Zhokhov
 */
@Internal
@Singleton
@Infrastructure
public final class GraphQLResolversRegistry {

    private final Map<BeanDefinition<?>, BeanDefinitionAndMethods> rootResolvers = new LinkedHashMap<>();
    private final Map<Class<?>, List<BeanDefinitionAndMethods>> typeResolvers = new HashMap<>();
    private final Map<BeanDefinition<?>, Map<String, Class<?>>> typeResolverContracts = new HashMap<>();
    private final Map<BeanDefinition<?>, List<ExecutableMethod<Object, ?>>> unassignedTypeResolverMethods =
            new HashMap<>();
    private final Map<BeanDefinition<?>, List<ExecutableMethod<Object, ?>>> pendingTypeResolverMethods =
            new LinkedHashMap<>();
    private final List<BeanDefinition<?>> unreadableTypeResolverContracts = new ArrayList<>();
    private final List<BeanDefinition<?>> unreadableRootMarkers = new ArrayList<>();
    private final Map<BeanDefinition<?>, ResolverMarkers> markers = new HashMap<>();
    private Function<BeanDefinition<?>, Map<String, Class<?>>> typeResolverContractReader =
            GraphQLResolversRegistry::readTypeResolverContracts;
    private final List<SchemaCoordinateMethod> schemaCoordinateMethods = new ArrayList<>();
    // by the signature of the method as the type model prints it
    private final Map<BeanDefinition<?>, Map<String, String>> schemaCoordinateSignatures = new HashMap<>();
    private final RuntimeTypeModel typeModel = new RuntimeTypeModel();

    private final BeanContext beanContext;
    private final boolean accessorPrefixes;
    private final Set<BeanDefinition<?>> seenRootResolvers = new HashSet<>();
    private final Set<BeanDefinition<?>> seenTypeResolvers = new HashSet<>();
    private final Set<BeanDefinition<?>> seenSchemaCoordinates = new HashSet<>();
    private boolean introductionBeansRegistered;
    private List<SchemaCoordinateMethod> sortedSchemaCoordinateMethods;

    public GraphQLResolversRegistry(BeanContext beanContext, GraphQLToolsConfiguration configuration) {
        this.beanContext = beanContext;
        this.accessorPrefixes = configuration.isResolverAccessorPrefixes();
    }

    private BeanDefinitionAndMethods newItem(BeanDefinition<?> beanDefinition) {
        BeanDefinitionAndMethods item = new BeanDefinitionAndMethods(beanDefinition, typeModel::method);
        item.bindingNames(it -> ResolverClassRules.boundNames(it, schemaCoordinateSignatures.get(beanDefinition),
                accessorPrefixes));
        return item;
    }

    public void registerRootResolverExecutableMethod(BeanDefinition<?> beanDefinition, ExecutableMethod<Object, ?> method) {
        seenRootResolvers.add(beanDefinition);
        if (beanDefinition.isAbstract()) {
            return;
        }
        ResolverMarkers markers = markersOf(beanDefinition);
        if (markers.operations().isEmpty() && implementsRootMarker(getUserBeanType(beanDefinition))) {
            if (!unreadableRootMarkers.contains(beanDefinition)) {
                unreadableRootMarkers.add(beanDefinition);
            }
            return;
        }
        if (!markers.admits(method.getMethodName())) {
            return;
        }
        rootResolvers.computeIfAbsent(beanDefinition, this::newItem).addExecutableMethod(method);
    }

    public void registerTypeResolverExecutableMethod(BeanDefinition<?> beanDefinition, ExecutableMethod<Object, ?> method) {
        seenTypeResolvers.add(beanDefinition);
        if (beanDefinition.isAbstract()) {
            return;
        }
        if (declaredModel(beanDefinition) == null && typeResolverContractReader.apply(beanDefinition).isEmpty()
                && !TypeResolver.class.isAssignableFrom(getUserBeanType(beanDefinition))) {
            throw new IncorrectAnnotationException(beanDefinition.getBeanType());
        }
        if (!markersOf(beanDefinition).admits(method.getMethodName())) {
            return;
        }

        pendingTypeResolverMethods.computeIfAbsent(beanDefinition, it -> new ArrayList<>()).add(method);
    }

    // the class that @GraphQLTypeResolver names on the bean, or null when the annotation names none (the marker
    // interface TypeResolver<T> names it by its type argument)
    @Nullable
    private static Class<?> declaredModel(BeanDefinition<?> beanDefinition) {
        return beanDefinition.getValue(GraphQLTypeResolver.class, AnnotationMetadata.VALUE_MEMBER)
                .map(it -> ((AnnotationClassValue<?>) it).getType().orElse(null))
                .filter(it -> it != void.class)
                .orElse(null);
    }

    // what the visitor wrote for the bean: the root operations and the contract methods of its marker interfaces
    private ResolverMarkers markersOf(BeanDefinition<?> beanDefinition) {
        return markers.computeIfAbsent(beanDefinition, GraphQLResolversRegistry::readMarkers);
    }

    static ResolverMarkers readMarkers(BeanDefinition<?> beanDefinition) {
        AnnotationMetadata metadata = beanDefinition.getAnnotationMetadata();
        String[] operations = metadata.stringValues(ResolverContracts.class, "operations");
        String[] methods = metadata.stringValues(ResolverContracts.class, "methods");
        return new ResolverMarkers(List.of(operations), null, methods.length == 0 ? null : Set.of(methods));
    }

    // a marker bean that the build did not process has no executable method that the registry is told about, so the startup
    // asks the bean context for the beans of each marker interface and reads what the build recorded for them
    private void findUnrecordedMarkerBeans() {
        for (Class<?> marker : List.of(QueryResolver.class, MutationResolver.class, SubscriptionResolver.class)) {
            for (BeanDefinition<?> beanDefinition : beanContext.getBeanDefinitions(marker)) {
                if (!beanDefinition.isAbstract() && markersOf(beanDefinition).operations().isEmpty()
                        && !unreadableRootMarkers.contains(beanDefinition)) {
                    unreadableRootMarkers.add(beanDefinition);
                }
            }
        }
        for (BeanDefinition<?> beanDefinition : beanContext.getBeanDefinitions(TypeResolver.class)) {
            if (!beanDefinition.isAbstract() && typeResolverContractReader.apply(beanDefinition).isEmpty()
                    && !unreadableTypeResolverContracts.contains(beanDefinition)) {
                unreadableTypeResolverContracts.add(beanDefinition);
            }
        }
    }

    private static boolean implementsRootMarker(Class<?> beanType) {
        return QueryResolver.class.isAssignableFrom(beanType) || MutationResolver.class.isAssignableFrom(beanType)
                || SubscriptionResolver.class.isAssignableFrom(beanType);
    }

    private void resolveTypeResolverMethods() {
        for (Map.Entry<BeanDefinition<?>, List<ExecutableMethod<Object, ?>>> entry
                : pendingTypeResolverMethods.entrySet()) {
            BeanDefinition<?> beanDefinition = entry.getKey();

            Map<String, Class<?>> contracts = typeResolverContractReader.apply(beanDefinition);
            if (contracts.isEmpty()) {
                // the visitor wrote no contracts: only a class that carries the annotation itself and has no supertype
                // besides Object, so that it inherits no other contract, is still a resolver
                Class<?> declared = declaredModel(beanDefinition);
                if (declared == null || !beanDefinition.hasDeclaredAnnotation(GraphQLTypeResolver.class)
                        || hasSupertype(getUserBeanType(beanDefinition))) {
                    unreadableTypeResolverContracts.add(beanDefinition);
                    continue;
                }
                contracts = Map.of(getUserBeanType(beanDefinition).getName(), declared);
            }
            typeResolverContracts.put(beanDefinition, contracts);
            Class<?> annotatedClass = contracts.values().iterator().next();

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
                    BeanDefinitionAndMethods item = newItem(beanDefinition);
                    typeResolvers.get(modelClass).add(item);
                    return item;
                })
                .addExecutableMethod(method);
    }

    void setTypeResolverContractReader(Function<BeanDefinition<?>, Map<String, Class<?>>> typeResolverContractReader) {
        this.typeResolverContractReader = typeResolverContractReader;
    }

    /**
     * Records a method that carries {@code @GraphQLSchemaCoordinate}, whatever the bean is.
     *
     * @param beanDefinition the bean definition
     * @param method         the method
     */
    public void registerSchemaCoordinateExecutableMethod(BeanDefinition<?> beanDefinition,
                                                         ExecutableMethod<Object, ?> method) {
        seenSchemaCoordinates.add(beanDefinition);
        JMethod view = typeModel.method(method);
        if (ResolverClassRules.isIgnored(view)) {
            return;
        }
        String coordinate = ResolverClassRules.declaredCoordinate(view);

        schemaCoordinateMethods.add(new SchemaCoordinateMethod(beanDefinition, method, coordinate,
                describeMethod(beanDefinition, method)));
        sortedSchemaCoordinateMethods = null;
        schemaCoordinateSignatures.computeIfAbsent(beanDefinition, it -> new HashMap<>())
                .put(view.signature(), coordinate);
        BeanDefinitionAndMethods root = rootResolvers.get(beanDefinition);
        if (root != null) {
            root.resetIndex();
        }
        typeResolvers.values().forEach(items -> items.stream()
                .filter(it -> it.getBeanDefinition().equals(beanDefinition))
                .forEach(BeanDefinitionAndMethods::resetIndex));
    }

    /**
     * Micronaut does not mark the bean definition of an introduction advice interface for startup method processing,
     * so the processors never see it when no other resolver bean needs them. Registers those beans' methods.
     */
    private void registerIntroductionBeans() {
        if (introductionBeansRegistered) {
            return;
        }
        introductionBeansRegistered = true;
        Collection<BeanDefinition<?>> roots = definitionsOf(GraphQLRootResolver.class);
        Collection<BeanDefinition<?>> types = definitionsOf(GraphQLTypeResolver.class);
        registerUnseen(GraphQLRootResolver.class, roots, seenRootResolvers, this::registerRootResolverExecutableMethod);
        registerUnseen(GraphQLTypeResolver.class, types, seenTypeResolvers, this::registerTypeResolverExecutableMethod);
        Set<BeanDefinition<?>> coordinateBeans = new LinkedHashSet<>(roots);
        coordinateBeans.addAll(types);
        coordinateBeans.addAll(definitionsOf(GraphQLSchemaCoordinate.class));
        registerUnseen(GraphQLSchemaCoordinate.class, coordinateBeans, seenSchemaCoordinates,
                this::registerSchemaCoordinateExecutableMethod);
    }

    private Collection<BeanDefinition<?>> definitionsOf(Class<? extends Annotation> annotation) {
        return beanContext.getBeanDefinitions(Qualifiers.byStereotype(annotation));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerUnseen(Class<? extends Annotation> annotation,
                                Collection<BeanDefinition<?>> definitions,
                                Set<BeanDefinition<?>> seen,
                                BiConsumer<BeanDefinition<?>, ExecutableMethod<Object, ?>> register) {
        for (BeanDefinition<?> beanDefinition : definitions) {
            if (seen.contains(beanDefinition)) {
                continue;
            }
            for (ExecutableMethod method : beanDefinition.getExecutableMethods()) {
                if (method.hasStereotype(annotation)) {
                    register.accept(beanDefinition, method);
                }
            }
        }
    }

    public List<SchemaCoordinateMethod> getSchemaCoordinateMethods() {
        registerIntroductionBeans();
        if (sortedSchemaCoordinateMethods == null) {
            sortedSchemaCoordinateMethods = schemaCoordinateMethods.stream()
                    .filter(it -> !isIgnored(it.method()))
                    .sorted(Comparator.comparing(SchemaCoordinateMethod::describe))
                    .toList();
        }
        return sortedSchemaCoordinateMethods;
    }

    /**
     * @param method a type resolver method
     * @return whether it declares its coordinate with {@code @GraphQLSchemaCoordinate}
     */
    public boolean declaresCoordinate(BeanDefinitionAndMethod method) {
        return ResolverClassRules.declaresCoordinate(typeModel.method(method.getExecutableMethod()),
                schemaCoordinateSignatures.get(method.getBeanDefinition()));
    }

    /**
     * @param coordinate a coordinate, {@code Type.field}
     * @return whether some resolver method declares it
     */
    public boolean isCoordinateDeclared(String coordinate) {
        return getSchemaCoordinateMethods().stream().anyMatch(it -> it.coordinate().equals(coordinate));
    }

    private boolean isCandidate(BeanDefinition<?> beanDefinition, ExecutableMethod<Object, ?> method) {
        return ResolverClassRules.isCandidate(typeModel.method(method), schemaCoordinateSignatures.get(beanDefinition));
    }

    private boolean isIgnored(ExecutableMethod<?, ?> method) {
        return ResolverClassRules.isIgnored(typeModel.method(method));
    }

    private boolean isLifecycleMethod(ExecutableMethod<?, ?> method) {
        return ResolverClassRules.isLifecycle(typeModel.method(method));
    }

    private List<BeanDefinitionAndMethod> findMethods(BeanDefinitionAndMethods item, String fieldName,
                                                      Set<String> coordinates) {
        Map<String, String> signatures = schemaCoordinateSignatures.get(item.getBeanDefinition());
        List<BeanDefinitionAndMethod> result = new ArrayList<>();
        for (Map.Entry<ExecutableMethod<Object, ?>, JMethod> method : item.methodsNamed(fieldName)) {
            if (ResolverClassRules.bindsField(method.getValue(), fieldName, coordinates, signatures, accessorPrefixes)) {
                result.add(new BeanDefinitionAndMethod(item.getBeanDefinition(), method.getKey()));
            }
        }
        return result;
    }

    // The visitor of graphql-tools-processor writes @ResolverContracts into the annotation metadata of the bean class.
    private static Map<String, Class<?>> readTypeResolverContracts(BeanDefinition<?> beanDefinition) {
        AnnotationMetadata metadata = beanDefinition.getAnnotationMetadata();
        Class<?>[] models = metadata.classValues(ResolverContracts.class, AnnotationMetadata.VALUE_MEMBER);
        String[] declaredBy = metadata.stringValues(ResolverContracts.class, "declaredBy");
        Map<String, Class<?>> contracts = new LinkedHashMap<>();
        for (int i = 0; i < models.length && i < declaredBy.length; i++) {
            contracts.put(declaredBy[i], models[i]);
        }
        return contracts;
    }

    // Runs only when the build recorded no contracts, never on a bound bean.
    private static boolean hasSupertype(Class<?> beanType) {
        // GroovyObject is added to every Groovy class by the compiler and is no contract
        return beanType.getSuperclass() != Object.class || Arrays.stream(beanType.getInterfaces())
                .anyMatch(it -> !it.getName().equals("groovy.lang.GroovyObject"));
    }

    /**
     * Fails when a bean implements a root marker interface and the build did not record its operation. It runs before the
     * check for a root resolver, because such a bean is not a root resolver for the registry without that record.
     */
    public void verifyRootMarkersRecorded() {
        findUnrecordedMarkerBeans();
        unreadableRootMarkers.stream()
                .min(Comparator.comparing((BeanDefinition<?> it) -> getUserBeanType(it).getName()))
                .ifPresent(beanDefinition -> {
                    throw TypeResolverContractsNotFoundException.forRootMarker(getUserBeanType(beanDefinition));
                });
    }

    public void verifyTypeResolverContracts() {
        registerIntroductionBeans();
        resolveTypeResolverMethods();
        findUnrecordedMarkerBeans();
        verifyRootMarkersRecorded();

        unreadableTypeResolverContracts.stream()
                .min(Comparator.comparing((BeanDefinition<?> it) -> getUserBeanType(it).getName()))
                .ifPresent(beanDefinition -> {
                    throw new TypeResolverContractsNotFoundException(getUserBeanType(beanDefinition),
                            beanDefinition.hasDeclaredAnnotation(GraphQLTypeResolver.class));
                });

        unassignedTypeResolverMethods.entrySet().stream()
                .sorted(Comparator.comparing(it -> it.getKey().getBeanType().getName()))
                .forEach(entry -> {
                    List<String> methods = entry.getValue().stream()
                            .filter(method -> isCandidate(entry.getKey(), method) && !isLifecycleMethod(method))
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

    public List<BeanDefinitionAndMethod> getResolverMethods() {
        List<BeanDefinitionAndMethods> items = new ArrayList<>(rootResolvers.values());
        typeResolvers.values().forEach(items::addAll);

        List<BeanDefinitionAndMethod> result = new ArrayList<>();
        for (BeanDefinitionAndMethods item : items) {
            for (ExecutableMethod<Object, ?> method : item.getExecutableMethods()) {
                if (isCandidate(item.getBeanDefinition(), method) && !isLifecycleMethod(method)) {
                    result.add(new BeanDefinitionAndMethod(item.getBeanDefinition(), method));
                }
            }
        }
        return result;
    }

    public List<BeanDefinitionAndMethod> getIgnoredMethods() {
        List<BeanDefinitionAndMethods> items = new ArrayList<>(rootResolvers.values());
        typeResolvers.values().forEach(items::addAll);

        List<BeanDefinitionAndMethod> result = new ArrayList<>();
        for (BeanDefinitionAndMethods item : items) {
            for (ExecutableMethod<Object, ?> method : item.getExecutableMethods()) {
                if (isIgnored(method)) {
                    result.add(new BeanDefinitionAndMethod(item.getBeanDefinition(), method));
                }
            }
        }
        unassignedTypeResolverMethods.forEach((beanDefinition, methods) -> methods.stream()
                .filter(this::isIgnored)
                .forEach(method -> result.add(new BeanDefinitionAndMethod(beanDefinition, method))));
        return result;
    }

    List<BeanDefinition<?>> rootResolverBeans() {
        return List.copyOf(rootResolvers.keySet());
    }

    List<BeanDefinition<?>> typeResolverBeans() {
        return typeResolvers.values().stream()
                .flatMap(List::stream)
                .<BeanDefinition<?>>map(BeanDefinitionAndMethods::getBeanDefinition)
                .distinct()
                .toList();
    }

    List<String> contractTypesOf(BeanDefinition<?> beanDefinition) {
        return typeResolverContracts.getOrDefault(beanDefinition, Map.of()).values().stream()
                .map(Class::getName)
                .sorted()
                .toList();
    }

    public boolean hasRootResolvers() {
        registerIntroductionBeans();
        return !rootResolvers.isEmpty();
    }

    public List<BeanDefinitionAndMethod> getRootExecutableMethods(String operation, String operationTypeName,
                                                           String methodName, MappingContext mappingContext) {
        List<BeanDefinitionAndMethod> all = new ArrayList<>();

        for (BeanDefinitionAndMethods item : rootResolvers.values()) {
            List<String> operations = markersOf(item.getBeanDefinition()).operations();
            if (operations.isEmpty() || operations.contains(operation)) {
                all.addAll(findMethods(item, methodName, Set.of(operationTypeName + "." + methodName)));
            }
        }

        if (!all.isEmpty()) {
            return all;
        }

        List<Class<?>> resolvers = rootResolvers.keySet().stream()
                .map(BeanDefinition::getBeanType)
                .sorted(Comparator.comparing((Function<Class<?>, String>) Class::getName))
                .collect(Collectors.toList());

        throw MethodNotFoundException.forRoot(methodName, mappingContext, resolvers,
                operationTypeName + "." + methodName, unannotatedMethods(methodName));
    }

    // the methods named as the missing one that a bean with coordinates does not annotate: they bind nothing
    private List<String> unannotatedMethods(String methodName) {
        List<String> result = new ArrayList<>();
        for (BeanDefinitionAndMethods item : rootResolvers.values()) {
            Map<String, String> signatures = schemaCoordinateSignatures.get(item.getBeanDefinition());
            if (signatures == null) {
                continue;
            }
            for (Map.Entry<ExecutableMethod<Object, ?>, JMethod> method : item.getMethodViews().entrySet()) {
                if (method.getValue().name().equals(methodName)
                        && !ResolverClassRules.isIgnored(method.getValue())
                        && !ResolverClassRules.isLifecycle(method.getValue())
                        && !signatures.containsKey(method.getValue().signature())) {
                    result.add(getUserBeanType(item.getBeanDefinition()).getName() + "#"
                            + MicronautUtils.getExecutableMethodFullName(method.getKey()));
                }
            }
        }
        return result.stream().sorted().toList();
    }

    public List<BeanDefinitionAndMethod> findTypeExecutableMethods(Class<?> beanType, String typeName, String methodName) {
        List<BeanDefinitionAndMethod> result = new ArrayList<>();

        for (BeanDefinitionAndMethods item : typeResolvers.getOrDefault(beanType, Collections.emptyList())) {
            result.addAll(findMethods(item, methodName, Set.of(typeName + "." + methodName)));
        }

        return result;
    }

    public MethodNotFoundException typeMethodNotFound(Class<?> beanType, String methodName, MappingContext mappingContext) {
        List<BeanDefinitionAndMethods> items = typeResolvers.get(beanType);

        List<Class<?>> resolvers = items != null
                ? items.stream()
                .map(it -> it.getBeanDefinition().getBeanType())
                .collect(Collectors.toList())
                : Collections.emptyList();

        return MethodNotFoundException.forType(methodName, mappingContext, beanType, resolvers);
    }

    public static String describeMethod(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return getUserBeanType(beanDefinition).getName() + " " + getExecutableMethodFullName(method);
    }

    /**
     * A method bound to a schema coordinate.
     *
     * @param beanDefinition the bean definition that declares the method
     * @param method         the method
     * @param coordinate     the schema coordinate the method is bound to
     * @param describe       the description of the method for messages
     */
    public record SchemaCoordinateMethod(BeanDefinition<?> beanDefinition, ExecutableMethod<Object, ?> method,
                                  String coordinate, String describe) {

    }

}
