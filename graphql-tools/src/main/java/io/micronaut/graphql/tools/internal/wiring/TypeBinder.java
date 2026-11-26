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
package io.micronaut.graphql.tools.internal.wiring;

import io.micronaut.graphql.tools.internal.wiring.interfaces.AbstractTypeWiring;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.MapEnumValuesProvider;
import graphql.schema.idl.TypeRuntimeWiring;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.EnumBeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.binding.internal.rules.EnumTypeRules;
import io.micronaut.graphql.tools.binding.internal.rules.ReturnVerdict;
import io.micronaut.graphql.tools.binding.internal.schema.WrapperRules;
import io.micronaut.graphql.tools.internal.exceptions.input.MissingEnumValuesException;
import io.micronaut.graphql.tools.internal.exceptions.mapping.ClassNotIntrospectedException;
import io.micronaut.graphql.tools.internal.exceptions.mapping.IncorrectClassMappingException;
import io.micronaut.graphql.tools.internal.exceptions.mapping.InterfaceTypeMappingNotProvidedException;
import io.micronaut.graphql.tools.internal.exceptions.mapping.MappingConflictException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.UnsupportedReturnTypeException;
import io.micronaut.graphql.tools.internal.input.EnumIntrospections;
import io.micronaut.graphql.tools.internal.input.TypeDefinitionMappingContext;
import io.micronaut.graphql.tools.internal.resolver.MicronautIntrospectionDataFetcher;
import io.micronaut.graphql.tools.internal.resolver.ReturnTypes;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import io.micronaut.graphql.tools.mapping.MappingContext;
import io.micronaut.graphql.tools.mapping.TypeMappingContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.graphql.tools.binding.internal.schema.ScalarCatalog.acceptsReturnClass;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarCatalog.getSupportedClasses;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarCatalog.isGraphQlBuiltInType;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarCatalog.isJavaBuiltInClass;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.getType;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.getTypeName;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.requireTypeName;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.unsupportedTypeDefinition;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.unwrapNonNullType;

/** Binds the schema types that a field returns (object, interface, union, enum, scalar) to Java classes. */
@Internal
final class TypeBinder {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator.class);

    private final WiringState s;
    private final BindingWalk walk;
    private final FieldProcessor fieldProcessor;

    TypeBinder(WiringState s, BindingWalk walk, FieldProcessor fieldProcessor) {
        this.s = s;
        this.walk = walk;
        this.fieldProcessor = fieldProcessor;
    }

    void processFieldReturnType(Argument<?> argument, Type<?> graphQlType, TypeMappingContext mappingContext) {
        // a property is read, never invoked: a Publisher can only be adapted from a method
        if (processFieldReturnType(argument, graphQlType, mappingContext, true, false, false).reactive() != ReturnVerdict.Reactive.NONE) {
            throw new UnsupportedReturnTypeException(mappingContext, WrapperRules.publisherOnProperty());
        }
    }

    ReturnVerdict processFieldReturnType(Argument<?> argument, Type<?> graphQlType,
                                                 TypeMappingContext mappingContext, boolean fieldLevel,
                                                 boolean singleResult, boolean suspend) {
        boolean subscription = mappingContext.getGraphQlObjectType().equals(s.subscriptionTypeName);
        ReturnVerdict verdict = ReturnTypes.adaptation(s.typeModel, s.typeDefinitionRegistry, argument, graphQlType, fieldLevel, subscription,
                singleResult, suspend, mappingContext);
        if (verdict.reactive() == ReturnVerdict.Reactive.VALUELESS) {
            // the field is null: no value type to check against the schema
            return verdict;
        }
        argument = ReturnTypes.unwrap(argument, verdict);
        graphQlType = unwrapNonNullType(graphQlType);

        Class<?> returnClass = argument.getType();

        if (graphQlType instanceof ListType listType) {
            if (!(Iterable.class.isAssignableFrom(returnClass) || Iterator.class.isAssignableFrom(returnClass))) {
                throw IncorrectClassMappingException.forField(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.ITERABLE,
                        mappingContext,
                        returnClass,
                        getSupportedClasses(listType)
                );
            }

            Type<?> listFieldType = (listType).getType();
            Argument<?> listArgument = argument.getFirstTypeVariable().get();

            processFieldReturnType(listArgument, listFieldType, mappingContext, false, false, false);
            return verdict;
        }

        TypeName typeName = requireTypeName(graphQlType);

        if (isGraphQlBuiltInType(typeName)) {
            s.manifestRecorder.scalar(typeName.getName());
            Set<Class<?>> supportedClasses = getSupportedClasses(getTypeName(graphQlType));

            if (!acceptsReturnClass(typeName.getName(), returnClass)) {
                throw IncorrectClassMappingException.forField(mappingContext, returnClass, supportedClasses);
            }
        } else {
            TypeDefinition<?> typeDefinition = s.typeDefinitionRegistry.getType(typeName.getName()).get();

            if (typeDefinition instanceof EnumTypeDefinition enumTypeDefinition) {
                processEnumTypeDefinition(enumTypeDefinition, returnClass, false, mappingContext);
            } else if (typeDefinition instanceof UnionTypeDefinition unionTypeDefinition) {
                walk.nested(() -> processUnionTypeDefinition(unionTypeDefinition, returnClass, mappingContext));
            } else if (typeDefinition instanceof ObjectTypeDefinition objectTypeDefinition) {
                walk.nested(() -> processObjectTypeDefinition(objectTypeDefinition, returnClass, mappingContext));
            } else if (typeDefinition instanceof InterfaceTypeDefinition interfaceTypeDefinition) {
                walk.nested(() -> processInterfaceTypeDefinition(interfaceTypeDefinition, returnClass, mappingContext));
            } else if (typeDefinition instanceof ScalarTypeDefinition scalarTypeDefinition) {
                GraphQLScalarMapping scalarMapping = s.scalarMappings.require(scalarTypeDefinition, mappingContext);

                if (scalarMapping.getOutputClasses().isEmpty()) {
                    throw IncorrectClassMappingException.forScalarWithoutClasses(mappingContext, returnClass,
                            scalarMapping.getName(), false);
                }
                if (!scalarMapping.isOutputClass(returnClass)) {
                    throw IncorrectClassMappingException.forField(mappingContext, returnClass,
                            scalarMapping.getOutputClasses());
                }
            } else {
                throw unsupportedTypeDefinition(typeDefinition);
            }
        }
        return verdict;
    }

    void processEnumTypeDefinition(EnumTypeDefinition enumTypeDefinition, Class<?> targetClass, boolean input,
                                           MappingContext mappingContext) {
        processIfNotProcessed(enumTypeDefinition, targetClass, mappingContext, () -> {
            if (!targetClass.isEnum()) {
                if (input) {
                    throw IncorrectClassMappingException.forArgument(
                            IncorrectClassMappingException.MappingType.DETECT_TYPE,
                            IncorrectClassMappingException.MappingType.ENUM,
                            mappingContext,
                            targetClass,
                            null
                    );
                } else {
                    throw IncorrectClassMappingException.forField(
                            IncorrectClassMappingException.MappingType.DETECT_TYPE,
                            IncorrectClassMappingException.MappingType.ENUM,
                            mappingContext,
                            targetClass,
                            null
                    );
                }
            }

            List<String> expectedValues = enumTypeDefinition.getEnumValueDefinitions().stream()
                    .map(EnumValueDefinition::getName)
                    .distinct()
                    .sorted()
                    .toList();

            EnumBeanIntrospection<?> introspection = EnumIntrospections.find(targetClass).orElseThrow(
                    () -> new ClassNotIntrospectedException(new TypeDefinitionMappingContext(enumTypeDefinition, targetClass, mappingContext),
                            targetClass, Introspected.class));
            Map<String, Object> constants = EnumIntrospections.constantsByName(introspection);

            List<String> missingValues = EnumTypeRules.check(expectedValues, s.typeModel.of(targetClass))
                    .missingSchemaValues();

            if (!missingValues.isEmpty()) {
                throw new MissingEnumValuesException(mappingContext, targetClass, missingValues);
            }

            Map<String, Object> values = new HashMap<>();
            expectedValues.forEach(it -> values.put(it, constants.get(it)));
            s.rootRuntimeWiringBuilder.type(enumTypeDefinition.getName(),
                    typeRuntimeWiringBuilder -> typeRuntimeWiringBuilder.enumValues(new MapEnumValuesProvider(values)));

            s.manifestRecorder.type(enumTypeDefinition.getName(), "enum", targetClass);
            expectedValues.forEach(it -> s.manifestRecorder.enumValue(enumTypeDefinition.getName(), it, targetClass));
        });
    }

    void processUnionTypeDefinition(UnionTypeDefinition unionTypeDefinition, Class<?> targetClass,
                                            TypeMappingContext mappingContext) {
        processIfNotProcessed(unionTypeDefinition, targetClass, mappingContext, () -> {
            if (!targetClass.isInterface()) {
                throw IncorrectClassMappingException.forField(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.INTERFACE,
                        mappingContext,
                        targetClass,
                        null
                );
            }

            s.manifestRecorder.type(unionTypeDefinition.getName(), "union", targetClass);

            s.abstractTypeWiring.wireUnion(unionTypeDefinition, targetClass, mappingContext,
                    (objectTypeDefinition, memberClass, memberContext) ->
                            walk.nested(() -> processObjectTypeDefinition(objectTypeDefinition, memberClass, memberContext)),
                    walk::inOrder);
        });
    }

    void processInterfaceTypeDefinition(InterfaceTypeDefinition interfaceTypeDefinition, Class<?> targetClass,
                                                TypeMappingContext mappingContext) {
        processIfNotProcessed(interfaceTypeDefinition, targetClass, mappingContext, () -> {
            if (!targetClass.isInterface()) {
                throw IncorrectClassMappingException.forField(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.INTERFACE,
                        mappingContext,
                        targetClass,
                        null
                );
            }

            s.manifestRecorder.type(interfaceTypeDefinition.getName(), "interface", targetClass);

            List<Runnable> members = new ArrayList<>();
            for (ObjectTypeDefinition implementation
                    : s.abstractTypeWiring.getImplementations(interfaceTypeDefinition)) {
                members.add(() -> {
                    Class<?> clazz = Optional
                            .ofNullable(s.abstractTypeWiring.findClass(implementation, targetClass, mappingContext))
                            .orElseThrow(() -> new InterfaceTypeMappingNotProvidedException(
                                    mappingContext, implementation.getName(), interfaceTypeDefinition.getName()
                            ));

                    AbstractTypeWiring.checkMemberClass("interface", interfaceTypeDefinition.getName(), targetClass,
                            implementation.getName(), clazz, mappingContext);

                    walk.nested(() -> processObjectTypeDefinition(implementation, clazz, mappingContext));
                });
            }
            walk.inOrder(members);
        });
    }

    void processObjectTypeDefinition(ObjectTypeDefinition objectTypeDefinition, Class<?> targetClass,
                                             TypeMappingContext mappingContext) {
        processIfNotProcessed(objectTypeDefinition, targetClass, mappingContext, () -> {
            Optional.ofNullable(s.schemaMappingDictionary.getTypes().get(objectTypeDefinition.getName()))
                    .ifPresent(registeredClass -> {
                        if (!bindsAlike(targetClass, registeredClass, mappingContext)) {
                            throw new MappingConflictException(
                                    mappingContext,
                                    getType(objectTypeDefinition),
                                    objectTypeDefinition.getName(),
                                    targetClass,
                                    registeredClass
                            );
                        }
                    });

            if (isJavaBuiltInClass(targetClass)) {
                throw IncorrectClassMappingException.forField(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.CUSTOM_CLASS,
                        mappingContext,
                        targetClass,
                        null
                );
            }

            BeanIntrospection<Object> beanIntrospection =
                    s.graphQLBeanIntrospectionRegistry.getGraphQlTypeBeanIntrospection(mappingContext, targetClass);
            if (beanIntrospection.getBeanType().isInterface()) {
                s.contractTypes.put(objectTypeDefinition.getName(), beanIntrospection.getBeanType());
            } else if (targetClass.isInterface()) {
                s.interfacesReadThroughImplementations.put(objectTypeDefinition.getName(),
                        List.of(targetClass, beanIntrospection.getBeanType()));
            }
            if (targetClass.isInterface()) {
                logInterfaceLookupMode(objectTypeDefinition.getName(), targetClass, beanIntrospection.getBeanType());
            }
            s.manifestRecorder.type(objectTypeDefinition.getName(), "object", beanIntrospection.getBeanType());

            TypeRuntimeWiring.Builder typeRuntimeWiringBuilder = TypeRuntimeWiring
                    .newTypeWiring(objectTypeDefinition.getName())
                    .defaultDataFetcher(new MicronautIntrospectionDataFetcher(beanIntrospection));
            s.objectTypeWirings.put(objectTypeDefinition.getName(), typeRuntimeWiringBuilder);

            List<FieldDefinition> fieldDefinitions = objectTypeDefinition.getFieldDefinitions();
            for (int i = fieldDefinitions.size() - 1; i >= 0; i--) {
                FieldDefinition fieldDefinition = fieldDefinitions.get(i);
                walk.push(() -> fieldProcessor.process(
                        fieldDefinition, objectTypeDefinition, typeRuntimeWiringBuilder, beanIntrospection),
                        TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName()));
            }
        });
    }

    static void logInterfaceLookupMode(String typeName, Class<?> interfaceClass, Class<?> beanType) {
        if (!LOG.isInfoEnabled()) {
            return;
        }
        if (beanType.isInterface()) {
            LOG.info("The type {} is mapped to the interface {}, which has its own bean introspection: its properties are "
                    + "the getters of the interface", typeName, interfaceClass.getName());
        } else {
            LOG.info("The type {} is mapped to the interface {}, which has no bean introspection of its own: its properties "
                    + "are all the getters of the implementation {}", typeName, interfaceClass.getName(), beanType.getName());
        }
    }

    void processIfNotProcessed(TypeDefinition<?> typeDefinition, Class<?> targetClass,
                                       MappingContext mappingContext, Runnable runnable) {
        if (s.processedTypes.containsKey(typeDefinition.getName())) {
            Class<?> processedClass = s.processedTypes.get(typeDefinition.getName());

            boolean sameBinding = typeDefinition instanceof ObjectTypeDefinition
                    ? bindsAlike(targetClass, processedClass, mappingContext)
                    : targetClass.equals(processedClass);

            if (!sameBinding) {
                throw new MappingConflictException(
                        mappingContext, getType(typeDefinition), typeDefinition.getName(), targetClass, processedClass
                );
            }

            return;
        }

        s.processedTypes.put(typeDefinition.getName(), targetClass);

        runnable.run();
    }

    boolean bindsAlike(Class<?> first, Class<?> second, MappingContext mappingContext) {
        if (first.equals(second)) {
            return true;
        }

        if (!s.graphQLBeanIntrospectionRegistry.getInterfaceClass(first)
                .equals(s.graphQLBeanIntrospectionRegistry.getInterfaceClass(second))) {
            return false;
        }

        return s.graphQLBeanIntrospectionRegistry.getGraphQlTypeBeanIntrospection(mappingContext, first).getBeanType()
                .equals(s.graphQLBeanIntrospectionRegistry.getGraphQlTypeBeanIntrospection(mappingContext, second)
                        .getBeanType());
    }

    /** Binds one field of an object type once the type is processed. */
    @FunctionalInterface
    interface FieldProcessor {

        void process(FieldDefinition fieldDefinition, ObjectTypeDefinition objectTypeDefinition,
                     TypeRuntimeWiring.Builder typeRuntimeWiringBuilder, BeanIntrospection<Object> beanIntrospection);
    }
}
