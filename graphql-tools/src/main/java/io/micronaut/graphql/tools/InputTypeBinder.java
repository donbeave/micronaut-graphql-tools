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
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.exceptions.ClassNotIntrospectedException;
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException;
import io.micronaut.graphql.tools.exceptions.InputFieldNotSettableException;
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException;
import io.micronaut.graphql.tools.exceptions.UnsupportedInputTypeException;
import io.micronaut.graphql.tools.schema.InputObjectConverter;
import io.micronaut.graphql.tools.schema.ValueConverter;
import io.micronaut.graphql.tools.schema.ValueConverters;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.micronaut.graphql.tools.GraphQLUtils.printType;
import static io.micronaut.graphql.tools.GraphQLUtils.requireTypeName;
import static io.micronaut.graphql.tools.GraphQLUtils.unsupportedTypeDefinition;
import static io.micronaut.graphql.tools.GraphQLUtils.unwrapNonNullType;
import static io.micronaut.graphql.tools.MicronautUtils.unwrapArgument;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarTable.getSupportedClasses;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarTable.isGraphQlBuiltInType;

/**
 * Binds the Java type of an argument or of an input field to its schema type: input objects, enums and scalars, recursively
 * through lists and input fields, and builds the {@link ValueConverter} that turns the value graphql-java coerced into
 * that Java type. Every converter is built here, at startup.
 */
@Internal
final class InputTypeBinder {

    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final ScalarMappings scalarMappings;
    private final GraphQLToolsConfiguration configuration;
    private final BindingManifestRecorder manifestRecorder;
    private final InputClassChecks inputClassChecks;
    private final RuntimeTypeModel typeModel;
    private final ApplicationContext applicationContext;
    private final Callbacks callbacks;
    // the converter of each input type, by the name of the type, registered before its fields are bound so that a type
    // that refers to itself finds it
    private final Map<String, ValueConverter> inputConverters = new HashMap<>();

    InputTypeBinder(TypeDefinitionRegistry typeDefinitionRegistry, ScalarMappings scalarMappings,
                    GraphQLToolsConfiguration configuration, BindingManifestRecorder manifestRecorder,
                    InputClassChecks inputClassChecks, RuntimeTypeModel typeModel,
                    ApplicationContext applicationContext, Callbacks callbacks) {
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.scalarMappings = scalarMappings;
        this.configuration = configuration;
        this.manifestRecorder = manifestRecorder;
        this.inputClassChecks = inputClassChecks;
        this.typeModel = typeModel;
        this.applicationContext = applicationContext;
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

            InputObjectConverter converter = new InputObjectConverter(beanIntrospection);
            ValueConverter custom = typeConverter(targetClass);
            inputConverters.put(inputObjectTypeDefinition.getName(), custom != null ? custom : converter);
            Set<String> mapped = new HashSet<>();
            Set<String> constructorArguments = Arrays.stream(beanIntrospection.getConstructorArguments())
                    .map(Argument::getName)
                    .collect(Collectors.toSet());

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

                ValueConverter fieldConverter = processInputType(inputValueDefinition.getType(),
                        property.get().asArgument(), inputMappingContext);

                manifestRecorder.inputField(inputObjectTypeDefinition.getName(), inputValueDefinition.getName(),
                        inputValueDefinition.getType(), beanIntrospection.getBeanType(), property.get());

                boolean argumentValue = ArgumentValue.class.equals(property.get().getType());
                if (property.get().isReadOnly() && !constructorArguments.contains(property.get().getName())) {
                    if (argumentValue) {
                        throw new UnsupportedInputTypeException(inputMappingContext, ArgumentValue.class.getName()
                                + " needs a writable property: it is set to omitted() when the client leaves it out.");
                    }
                    throw new InputFieldNotSettableException(inputMappingContext);
                }

                converter.addField(inputValueDefinition.getName(), property.get(), fieldConverter, argumentValue);
                mapped.add(property.get().getName());
            }
            // an ArgumentValue that this input type does not define, e.g. one that another input type backed by the same
            // class defines, is omitted
            for (BeanProperty<Object, Object> property : beanIntrospection.getBeanProperties()) {
                if (ArgumentValue.class.equals(property.getType()) && !mapped.contains(property.getName())
                        && (!property.isReadOnly() || constructorArguments.contains(property.getName()))) {
                    converter.addField(property.getName(), property, ValueConverters.IDENTITY, true);
                }
            }
            converter.seal();

            inputClassChecks.collectUnmappedInputProperties(inputObjectTypeDefinition,
                    typeModel.of(beanIntrospection), beanIntrospection.getBeanType());

            manifestRecorder.type(inputObjectTypeDefinition.getName(), "input", targetClass);
        });
    }

    ValueConverter processInputType(Type<?> graphQlType, Argument<?> argument, MappingContext mappingContext) {
        return processInputType(graphQlType, argument, mappingContext, true);
    }

    private ValueConverter processInputType(Type<?> graphQlType, Argument<?> argument, MappingContext mappingContext,
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

            if (!ValueConverters.isSupportedList(returnType)) {
                throw new UnsupportedInputTypeException(mappingContext, "a list is converted into a List, a "
                        + "Collection, an Iterable, a Set, a SortedSet or a Deque, but the declared type is "
                        + returnType.getName() + ".");
            }

            Type<?> listFieldType = ((ListType) fieldType).getType();
            Argument<?> listArgument = argument.getFirstTypeVariable().get();

            return ValueConverters.list(returnType,
                    processInputType(listFieldType, listArgument, mappingContext, false));
        }

        TypeName typeName = requireTypeName(graphQlType);

        TypeDefinition<?> typeDefinition = typeDefinitionRegistry.getType(typeName).get();

        if (typeDefinition instanceof InputObjectTypeDefinition inputObjectTypeDefinition) {
            processInputObjectTypeDefinition(
                    inputObjectTypeDefinition,
                    returnType,
                    mappingContext
            );
            return inputConverters.get(inputObjectTypeDefinition.getName());
        } else if (typeDefinition instanceof EnumTypeDefinition enumTypeDefinition) {
            callbacks.processEnum(enumTypeDefinition, returnType, true, mappingContext);
            return ValueConverters.IDENTITY;
        } else if (isGraphQlBuiltInType(typeName)) {
            manifestRecorder.scalar(typeName.getName());
            Set<Class<?>> supportedClasses = getSupportedClasses(typeName);

            if (!supportedClasses.contains(returnType)) {
                throw IncorrectClassMappingException.forArgument(mappingContext, returnType, supportedClasses);
            }
            if (returnType.isPrimitive() && !nonNull && configuration.isStrict()) {
                throw IncorrectClassMappingException.forNullablePrimitiveInStrictMode(mappingContext, returnType,
                        typeName.getName(), supportedClasses);
            }
            return ValueConverters.builtIn(returnType);
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
            if (returnType.isPrimitive() && !nonNull) {
                throw IncorrectClassMappingException.forNullablePrimitive(mappingContext, returnType,
                        scalarMapping.getName(), scalarMapping.getInputClasses());
            }
            return ValueConverters.scalar(returnType, conversionService());
        } else {
            throw unsupportedTypeDefinition(typeDefinition);
        }
    }

    private ConversionService conversionService() {
        return applicationContext.getConversionService();
    }

    /**
     * @param target an input class
     * @return the converter of the application's {@code TypeConverter<Map, target>} bean, if it has one; the library
     * builds the instance from the introspection otherwise
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ValueConverter typeConverter(Class<?> target) {
        for (BeanDefinition<TypeConverter> definition : applicationContext.getBeanDefinitions(TypeConverter.class)) {
            var typeArguments = definition.getTypeArguments(TypeConverter.class);
            if (typeArguments.size() == 2 && typeArguments.get(1).getType().equals(target)
                    && typeArguments.get(0).getType().isAssignableFrom(Map.class)) {
                TypeConverter converter = applicationContext.getBean(definition);
                return value -> {
                    if (value == null || target.isInstance(value)) {
                        return value;
                    }
                    Optional<?> converted = converter.convert(value, target, ConversionContext.DEFAULT);
                    if (converted.isEmpty()) {
                        throw new IllegalArgumentException("The TypeConverter bean " + converter.getClass().getName()
                                + " cannot convert the input object `" + value + "` to " + target.getName());
                    }
                    return converted.get();
                };
            }
        }
        return null;
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
