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
package io.micronaut.graphql.tools;

import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.internal.exceptions.AmbiguousAbstractTypeException;
import io.micronaut.graphql.tools.internal.exceptions.IncorrectMemberMappingException;
import io.micronaut.graphql.tools.internal.exceptions.UnionTypeMappingNotProvidedException;
import io.micronaut.graphql.tools.schema.AbstractTypeResolver;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import static io.micronaut.graphql.tools.GraphQLUtils.getTypeName;
import static io.micronaut.graphql.tools.GraphQLUtils.unsupportedTypeDefinition;

/**
 * The phase that wires the type resolution of unions and interfaces: which Java class is which member type, and whether
 * two member types are indistinguishable. The resolvers it creates stay inside it until the ambiguity check has read them.
 */
@Internal
final class AbstractTypeWiring {

    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final SchemaMappingDictionary schemaMappingDictionary;
    private final GraphQLBeanIntrospectionRegistry graphQLBeanIntrospectionRegistry;
    private final BindingManifestRecorder manifestRecorder;
    private final RuntimeWiring.Builder runtimeWiringBuilder;
    private final List<AbstractTypeResolver> abstractTypeResolvers = new ArrayList<>();
    private Map<String, List<ObjectTypeDefinition>> implementations;

    AbstractTypeWiring(TypeDefinitionRegistry typeDefinitionRegistry, SchemaMappingDictionary schemaMappingDictionary,
                       GraphQLBeanIntrospectionRegistry graphQLBeanIntrospectionRegistry,
                       BindingManifestRecorder manifestRecorder, RuntimeWiring.Builder runtimeWiringBuilder) {
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.schemaMappingDictionary = schemaMappingDictionary;
        this.graphQLBeanIntrospectionRegistry = graphQLBeanIntrospectionRegistry;
        this.manifestRecorder = manifestRecorder;
        this.runtimeWiringBuilder = runtimeWiringBuilder;
    }

    void wireUnion(UnionTypeDefinition unionTypeDefinition, Class<?> targetClass, TypeMappingContext mappingContext,
                   MemberBinder memberBinder, Consumer<List<Runnable>> inOrder) {
        Map<Class<?>, String> exactClasses = new HashMap<>();
        Map<String, Class<?>> objectTypes = new HashMap<>();
        List<Runnable> actions = new ArrayList<>();

        for (Type<?> type : unionTypeDefinition.getMemberTypes()) {
            actions.add(() -> {
                TypeName typeName = getTypeName(type);
                TypeDefinition<?> typeDefinition = typeDefinitionRegistry.getType(typeName).get();

                if (typeDefinition instanceof ObjectTypeDefinition objectTypeDefinition) {
                    Class<?> clazz = Optional
                            .ofNullable(schemaMappingDictionary.getTypes().get(typeDefinition.getName()))
                            .orElseThrow(() -> new UnionTypeMappingNotProvidedException(
                                    mappingContext, typeName.getName(), unionTypeDefinition.getName()
                            ));

                    checkMemberClass("union", unionTypeDefinition.getName(), targetClass, typeName.getName(), clazz,
                            mappingContext);

                    memberBinder.bind(objectTypeDefinition, clazz, mappingContext);

                    objectTypes.put(typeName.getName(), clazz);
                    manifestRecorder.resolves(unionTypeDefinition.getName(), typeName.getName(), clazz);
                    for (Class<?> mappedClass : getMappedClasses(clazz)) {
                        exactClasses.put(mappedClass, typeName.getName());
                    }
                } else {
                    throw unsupportedTypeDefinition(typeDefinition);
                }
            });
        }

        actions.add(() -> {
            AbstractTypeResolver typeResolver = new AbstractTypeResolver(
                    "union", unionTypeDefinition.getName(), exactClasses, objectTypes
            );
            abstractTypeResolvers.add(typeResolver);
            runtimeWiringBuilder.type(unionTypeDefinition.getName(),
                    typeRuntimeWiringBuilder -> typeRuntimeWiringBuilder.typeResolver(typeResolver));
        });
        inOrder.accept(actions);
    }

    /**
     * Wires the type resolver of every interface that a field returns or that a bound object type implements.
     *
     * @param processedTypes the Java class that each schema type was bound to
     * @return the interfaces that no field returns but a bound object type implements
     */
    Set<String> wireInterfaces(Map<String, Class<?>> processedTypes) {
        Set<String> implementedInterfaces = new TreeSet<>();

        List<InterfaceTypeDefinition> interfaceTypeDefinitions =
                typeDefinitionRegistry.getTypes(InterfaceTypeDefinition.class).stream()
                        .sorted(Comparator.comparing(InterfaceTypeDefinition::getName))
                        .toList();

        for (InterfaceTypeDefinition interfaceTypeDefinition : interfaceTypeDefinitions) {
            String name = interfaceTypeDefinition.getName();
            boolean returned = processedTypes.containsKey(name);

            Map<Class<?>, String> exactClasses = new HashMap<>();
            Map<String, Class<?>> objectTypes = new HashMap<>();
            Set<Class<?>> sharedClasses = new HashSet<>();
            boolean implemented = false;
            for (ObjectTypeDefinition implementation : getImplementations(interfaceTypeDefinition)) {
                Class<?> clazz = getBoundClass(implementation, processedTypes);
                if (clazz != null) {
                    implemented = true;
                    objectTypes.put(implementation.getName(), clazz);
                    for (Class<?> mappedClass : getMappedClasses(clazz)) {
                        if (exactClasses.putIfAbsent(mappedClass, implementation.getName()) != null) {
                            sharedClasses.add(mappedClass);
                        }
                    }
                }
            }
            sharedClasses.forEach(exactClasses::remove);

            if (!returned && !implemented) {
                continue;
            }
            if (!returned) {
                implementedInterfaces.add(name);
                manifestRecorder.type(name, "interface", null);
            }
            objectTypes.forEach((objectType, clazz) -> manifestRecorder.resolves(name, objectType, clazz));

            AbstractTypeResolver typeResolver = new AbstractTypeResolver("interface", name,
                    exactClasses, objectTypes);
            if (returned) {
                abstractTypeResolvers.add(typeResolver);
            }
            runtimeWiringBuilder.type(name, typeRuntimeWiringBuilder -> typeRuntimeWiringBuilder
                    .typeResolver(typeResolver));
        }

        return implementedInterfaces;
    }

    void checkAmbiguity() {
        List<AbstractTypeResolver> resolvers = abstractTypeResolvers.stream()
                .filter(it -> it.getObjectTypeCount() > 1)
                .toList();
        if (resolvers.isEmpty()) {
            return;
        }

        List<String> ambiguities = new ArrayList<>();
        graphQLBeanIntrospectionRegistry.getIntrospectedClasses().stream()
                .sorted(Comparator.comparing(Class::getName))
                .forEach(introspectedClass -> {
                    for (AbstractTypeResolver resolver : resolvers) {
                        String ambiguity = resolver.findAmbiguity(introspectedClass);
                        if (ambiguity != null) {
                            ambiguities.add(ambiguity);
                        }
                    }
                });

        if (!ambiguities.isEmpty()) {
            throw new AmbiguousAbstractTypeException(ambiguities);
        }
    }

    @Nullable
    private Class<?> getBoundClass(ObjectTypeDefinition objectTypeDefinition,
                                   Map<String, Class<?>> processedTypes) {
        Class<?> processedClass = processedTypes.get(objectTypeDefinition.getName());
        if (processedClass == null) {
            return null;
        }
        return schemaMappingDictionary.getTypes().getOrDefault(objectTypeDefinition.getName(), processedClass);
    }

    static void checkMemberClass(String abstractTypeKind, String abstractTypeName, Class<?> declaredClass,
                                         String objectTypeName, Class<?> memberClass,
                                         TypeMappingContext mappingContext) {
        if (areDisjoint(declaredClass, memberClass)) {
            throw new IncorrectMemberMappingException(mappingContext, abstractTypeKind, abstractTypeName,
                    declaredClass, objectTypeName, memberClass);
        }
    }

    private static boolean areDisjoint(Class<?> a, Class<?> b) {
        if (a.isAssignableFrom(b) || b.isAssignableFrom(a)) {
            return false;
        }
        return !a.isInterface() && !b.isInterface()
                || Modifier.isFinal(a.getModifiers())
                || Modifier.isFinal(b.getModifiers());
    }

    private List<Class<?>> getMappedClasses(Class<?> clazz) {
        if (!clazz.isInterface()) {
            return List.of(clazz);
        }

        List<Class<?>> result = new ArrayList<>();
        result.add(clazz);
        result.addAll(graphQLBeanIntrospectionRegistry.getImplementationClasses(clazz));
        return result;
    }

    List<ObjectTypeDefinition> getImplementations(InterfaceTypeDefinition interfaceTypeDefinition) {
        if (implementations == null) {
            implementations = indexImplementations();
        }
        return implementations.getOrDefault(interfaceTypeDefinition.getName(), List.of());
    }

    // the registry scans every type for each interface it is asked about; one pass indexes them all
    private Map<String, List<ObjectTypeDefinition>> indexImplementations() {
        Map<String, List<ObjectTypeDefinition>> index = new HashMap<>();
        typeDefinitionRegistry.getTypes(ObjectTypeDefinition.class).stream()
                .sorted(Comparator.comparing(ObjectTypeDefinition::getName))
                .forEach(objectType -> objectType.getImplements().stream()
                        .map(it -> getTypeName(it).getName())
                        .distinct()
                        .forEach(name -> index.computeIfAbsent(name, it -> new ArrayList<>()).add(objectType)));
        return index;
    }

    @FunctionalInterface
    /**
     * Binds the Java class of one member type of an abstract type, as the type phase does for any object type.
     */
    interface MemberBinder {

        void bind(ObjectTypeDefinition objectTypeDefinition, Class<?> targetClass, TypeMappingContext mappingContext);
    }

}
