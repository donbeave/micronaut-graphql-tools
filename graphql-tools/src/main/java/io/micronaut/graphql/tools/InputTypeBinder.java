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

import graphql.language.EnumTypeDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules;
import io.micronaut.graphql.tools.exceptions.ClassNotIntrospectedException;
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException;
import io.micronaut.graphql.tools.exceptions.InputFieldNotSettableException;
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException;
import io.micronaut.graphql.tools.exceptions.UnsupportedInputTypeException;
import io.micronaut.graphql.tools.schema.InputValueConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.graphql.tools.GraphQLUtils.printType;
import static io.micronaut.graphql.tools.GraphQLUtils.requireTypeName;
import static io.micronaut.graphql.tools.GraphQLUtils.unsupportedTypeDefinition;
import static io.micronaut.graphql.tools.GraphQLUtils.unwrapNonNullType;
import static io.micronaut.graphql.tools.MicronautUtils.unwrapArgument;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarTable.getSupportedClasses;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarTable.isGraphQlBuiltInType;

/**
 * Binds the Java type of an argument or of an input field to its schema type: input objects, enums and scalars, recursively
 * through lists and input fields. The omission plans of the input classes it finds are written into the map that the input
 * converter reads.
 */
@Internal
final class InputTypeBinder {

    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final ScalarMappings scalarMappings;
    private final GraphQLToolsConfiguration configuration;
    private final BindingManifestRecorder manifestRecorder;
    private final InputClassChecks inputClassChecks;
    private final Map<Class<?>, InputValueConverter.OmissionPlan> omissionPlans;
    private final RuntimeTypeModel typeModel;
    private final Callbacks callbacks;

    InputTypeBinder(TypeDefinitionRegistry typeDefinitionRegistry, ScalarMappings scalarMappings,
                    GraphQLToolsConfiguration configuration, BindingManifestRecorder manifestRecorder,
                    InputClassChecks inputClassChecks, Map<Class<?>, InputValueConverter.OmissionPlan> omissionPlans,
                    RuntimeTypeModel typeModel, Callbacks callbacks) {
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.scalarMappings = scalarMappings;
        this.configuration = configuration;
        this.manifestRecorder = manifestRecorder;
        this.inputClassChecks = inputClassChecks;
        this.omissionPlans = omissionPlans;
        this.typeModel = typeModel;
        this.callbacks = callbacks;
    }

    private void processInputObjectTypeDefinition(InputObjectTypeDefinition inputObjectTypeDefinition,
                                                  Class<?> targetClass, MappingContext mappingContext) {
        callbacks.processIfNotProcessed(inputObjectTypeDefinition, targetClass, mappingContext, () -> {
            if (targetClass.isInterface()) {
                throw IncorrectClassMappingException.forArgument(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.CUSTOM_CLASS,
                        mappingContext,
                        targetClass,
                        null
                );
            }

            BeanIntrospection<Object> beanIntrospection = BeanIntrospector.SHARED
                    .findIntrospection((Class<Object>) targetClass)
                    .orElseThrow(() -> new ClassNotIntrospectedException(mappingContext, targetClass, GraphQLInput.class));

            if (
                    beanIntrospection.getBeanType().isInterface()
                            || beanIntrospection.getBeanType().isEnum()
                            || beanIntrospection.getBeanType().isAnnotation()
            ) {
                throw IncorrectClassMappingException.forArgument(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.CUSTOM_CLASS,
                        mappingContext,
                        targetClass,
                        null
                );
            }

            Set<String> settableProperties = inputClassChecks.findSettableProperties(targetClass);
            if (settableProperties == null) {
                inputClassChecks.addOwnDeserializer(inputObjectTypeDefinition.getName(), targetClass);
            }
            boolean detectOmission = configuration.getInput().isDetectOmission();
            List<BeanProperty<Object, Object>> argumentValueProperties = new ArrayList<>();
            List<BeanProperty<Object, Object>> inputObjectProperties = new ArrayList<>();

            for (InputValueDefinition inputValueDefinition : inputObjectTypeDefinition.getInputValueDefinitions()) {
                Optional<BeanProperty<Object, Object>> property =
                        beanIntrospection.getProperty(inputValueDefinition.getName());

                if (property.isEmpty()) {
                    throw MethodNotFoundException.forInput(
                            inputValueDefinition.getName(),
                            new InputMappingContext(
                                    inputObjectTypeDefinition,
                                    inputValueDefinition.getName(),
                                    targetClass,
                                    null
                            ),
                            beanIntrospection.getBeanType()
                    );
                }

                InputMappingContext inputMappingContext = new InputMappingContext(
                        inputObjectTypeDefinition,
                        inputValueDefinition.getName(),
                        targetClass,
                        property.get().getName()
                );

                processInputType(inputValueDefinition.getType(), property.get().asArgument(), inputMappingContext);

                manifestRecorder.inputField(inputObjectTypeDefinition.getName(), inputValueDefinition.getName(),
                        inputValueDefinition.getType(), beanIntrospection.getBeanType(), property.get());

                if (ArgumentValue.class.equals(property.get().getType()) && property.get().isReadOnly()) {
                    throw new UnsupportedInputTypeException(inputMappingContext, ArgumentValue.class.getName()
                            + " needs a writable property: it is set to omitted() when the client leaves it out.");
                }

                if (settableProperties != null && !settableProperties.contains(inputValueDefinition.getName())) {
                    throw new InputFieldNotSettableException(inputMappingContext);
                }

                if (detectOmission) {
                    if (ArgumentValue.class.equals(property.get().getType())) {
                        if (property.get().isWriteOnly()) {
                            throw new UnsupportedInputTypeException(inputMappingContext, ArgumentValue.class.getName()
                                    + " needs a readable property: it is read to set it to omitted() when the client "
                                    + "leaves it out.");
                        }
                        argumentValueProperties.add(property.get());
                    } else if (isInputObjectType(inputValueDefinition.getType())) {
                        if (property.get().isWriteOnly()) {
                            inputClassChecks.addWriteOnlyInputObjectProperty(inputMappingContext,
                                    InputClassChecks.elementClass(property.get().asArgument()));
                        } else {
                            inputObjectProperties.add(property.get());
                        }
                    }
                }
            }

            if (!argumentValueProperties.isEmpty() || !inputObjectProperties.isEmpty()) {
                omissionPlans.merge(targetClass,
                        new InputValueConverter.OmissionPlan(argumentValueProperties, inputObjectProperties),
                        InputValueConverter.OmissionPlan::merge);
            }

            inputClassChecks.collectUnmappedInputProperties(inputObjectTypeDefinition,
                    typeModel.of(beanIntrospection), beanIntrospection.getBeanType());

            manifestRecorder.type(inputObjectTypeDefinition.getName(), "input", targetClass);
        });
    }

    void processInputType(Type<?> graphQlType, Argument<?> argument, MappingContext mappingContext) {
        processInputType(graphQlType, argument, mappingContext, true);
    }

    private boolean isInputObjectType(Type<?> graphQlType) {
        Type<?> type = graphQlType;
        while (!(type instanceof TypeName)) {
            type = type instanceof NonNullType nonNullType ? nonNullType.getType() : ((ListType) type).getType();
        }
        return typeDefinitionRegistry.getType(((TypeName) type).getName())
                .filter(InputObjectTypeDefinition.class::isInstance)
                .isPresent();
    }

    private void processInputType(Type<?> graphQlType, Argument<?> argument, MappingContext mappingContext,
                                  boolean fieldLevel) {
        argument = unwrapArgumentValue(unwrapArgument(argument), graphQlType, fieldLevel, mappingContext);
        boolean nonNull = graphQlType instanceof NonNullType;
        graphQlType = unwrapNonNullType(graphQlType);

        Type<?> fieldType = unwrapNonNullType(graphQlType);
        Class<?> returnType = argument.getType();

        if (fieldType instanceof ListType listType) {
            if (!(Iterable.class.isAssignableFrom(returnType))) {
                throw IncorrectClassMappingException.forArgument(
                        IncorrectClassMappingException.MappingType.DETECT_TYPE,
                        IncorrectClassMappingException.MappingType.ITERABLE,
                        mappingContext,
                        returnType,
                        getSupportedClasses(listType)
                );
            }

            Type<?> listFieldType = ((ListType) fieldType).getType();
            Argument<?> listArgument = argument.getFirstTypeVariable().get();

            processInputType(listFieldType, listArgument, mappingContext, false);
            return;
        }

        TypeName typeName = requireTypeName(graphQlType);

        TypeDefinition<?> typeDefinition = typeDefinitionRegistry.getType(typeName).get();

        if (typeDefinition instanceof InputObjectTypeDefinition inputObjectTypeDefinition) {
            processInputObjectTypeDefinition(
                    inputObjectTypeDefinition,
                    returnType,
                    mappingContext
            );
        } else if (typeDefinition instanceof EnumTypeDefinition enumTypeDefinition) {
            callbacks.processEnum(enumTypeDefinition, returnType, true, mappingContext);
        } else if (isGraphQlBuiltInType(typeName)) {
            manifestRecorder.scalar(typeName.getName());
            Set<Class<?>> supportedClasses = getSupportedClasses(typeName);

            if (!supportedClasses.contains(returnType)) {
                throw IncorrectClassMappingException.forArgument(mappingContext, returnType, supportedClasses);
            }
            if (configuration.isStrict() && ResolverClassRules.nullablePrimitive(typeModel.ref(argument), nonNull)) {
                throw IncorrectClassMappingException.forNullablePrimitiveInStrictMode(mappingContext, returnType,
                        typeName.getName(), supportedClasses);
            }
        } else if (typeDefinition instanceof ScalarTypeDefinition scalarTypeDefinition) {
            GraphQLScalarMapping scalarMapping = scalarMappings.require(scalarTypeDefinition, mappingContext);

            if (scalarMapping.getInputClasses().isEmpty()) {
                throw IncorrectClassMappingException.forScalarWithoutClasses(mappingContext, returnType,
                        scalarMapping.getName(), true);
            }
            if (!scalarMapping.isInputClass(returnType)) {
                throw IncorrectClassMappingException.forArgument(mappingContext, returnType,
                        scalarMapping.getInputClasses());
            }
            if (ResolverClassRules.nullablePrimitive(typeModel.ref(argument), nonNull)) {
                throw IncorrectClassMappingException.forNullablePrimitive(mappingContext, returnType,
                        scalarMapping.getName(), scalarMapping.getInputClasses());
            }
        } else {
            throw unsupportedTypeDefinition(typeDefinition);
        }
    }

    private Argument<?> unwrapArgumentValue(Argument<?> argument, Type<?> graphQlType, boolean fieldLevel,
                                            MappingContext mappingContext) {
        if (!ArgumentValue.class.equals(argument.getType())) {
            return argument;
        }
        String wrapper = ArgumentValue.class.getName();
        if (!configuration.getInput().isDetectOmission()) {
            throw new UnsupportedInputTypeException(mappingContext,
                    wrapper + " needs " + GraphQLToolsConfiguration.PREFIX + ".input.detect-omission=true.");
        }
        if (!fieldLevel) {
            throw new UnsupportedInputTypeException(mappingContext,
                    wrapper + " is not allowed inside a list: a list element cannot be omitted.");
        }
        if (graphQlType instanceof NonNullType) {
            throw new UnsupportedInputTypeException(mappingContext, wrapper
                    + " is only allowed for a nullable GraphQL type, the type is " + printType(graphQlType) + ".");
        }
        Argument<?> result = argument.getFirstTypeVariable()
                .orElseThrow(() -> new UnsupportedInputTypeException(mappingContext,
                        wrapper + " must declare the type it wraps."));
        if (ArgumentValue.class.equals(result.getType())) {
            throw new UnsupportedInputTypeException(mappingContext, wrapper + " cannot wrap " + wrapper + ".");
        }
        return result;
    }

    /**
     * What this phase needs from the type phase: a type is bound once, whichever side reaches it first, and an enum is
     * bound the same way for an argument and for a return type.
     */
    interface Callbacks {

        void processIfNotProcessed(TypeDefinition<?> typeDefinition, Class<?> targetClass,
                                   MappingContext mappingContext, Runnable runnable);

        void processEnum(EnumTypeDefinition enumTypeDefinition, Class<?> targetClass, boolean input,
                         MappingContext mappingContext);
    }

}
