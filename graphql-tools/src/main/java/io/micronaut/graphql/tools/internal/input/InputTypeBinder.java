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
package io.micronaut.graphql.tools.internal.input;

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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.ArgumentValue;
import io.micronaut.graphql.tools.GraphQLScalarMapping;
import io.micronaut.graphql.tools.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.InputMappingContext;
import io.micronaut.graphql.tools.MappingContext;
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.rules.InputTypeRules;
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules;
import io.micronaut.graphql.tools.internal.checks.CheckPolicy;
import io.micronaut.graphql.tools.internal.exceptions.ClassNotIntrospectedException;
import io.micronaut.graphql.tools.internal.exceptions.IncorrectClassMappingException;
import io.micronaut.graphql.tools.internal.exceptions.InputFieldNotSettableException;
import io.micronaut.graphql.tools.internal.exceptions.MethodNotFoundException;
import io.micronaut.graphql.tools.internal.exceptions.UnsupportedInputTypeException;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestRecorder;
import io.micronaut.graphql.tools.internal.model.RuntimeTypeModel;
import io.micronaut.inject.BeanDefinition;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.micronaut.graphql.tools.binding.internal.schema.ScalarTable.getSupportedClasses;
import static io.micronaut.graphql.tools.binding.internal.schema.ScalarTable.isGraphQlBuiltInType;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.printType;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.requireTypeName;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.unsupportedTypeDefinition;
import static io.micronaut.graphql.tools.internal.GraphQLUtils.unwrapNonNullType;
import static io.micronaut.graphql.tools.internal.MicronautUtils.unwrapArgument;

/**
 * Binds the Java type of an argument or of an input field to its schema type: input objects, enums and scalars, recursively
 * through lists and input fields, and builds the {@link ValueConverter} that turns the value graphql-java coerced into
 * that Java type. Every converter is built here, at startup.
 */
@Internal
public final class InputTypeBinder {

    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final ScalarMappings scalarMappings;
    private final GraphQLToolsConfiguration configuration;
    private final CheckPolicy policy;
    private final List<String> nullablePrimitiveWarnings = new ArrayList<>();
    private final BindingManifestRecorder manifestRecorder;
    private final InputClassChecks.Builder inputClassChecks = new InputClassChecks.Builder();
    private final RuntimeTypeModel typeModel;
    private final ApplicationContext applicationContext;
    private final Callbacks callbacks;
    // the converter of each input type, by the name of the type, registered before its fields are bound so that a type
    // that refers to itself finds it
    private final Map<String, ValueConverter> inputConverters = new HashMap<>();
    // the failure of an input type whose binding failed, so that every later use reports it again
    private final Map<String, RuntimeException> inputFailures = new HashMap<>();
    // The fields of the input types are bound from this stack of steps, not by one recursive call per type: the depth of
    // a recursive walk is the length of the longest chain of input types. A step runs before the steps below it, so the
    // order is the one of a recursive walk.
    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private TypeFrame current;

  public   InputTypeBinder(TypeDefinitionRegistry typeDefinitionRegistry, ScalarMappings scalarMappings,
                    GraphQLToolsConfiguration configuration, CheckPolicy policy,
                    BindingManifestRecorder manifestRecorder,
                    RuntimeTypeModel typeModel,
                    ApplicationContext applicationContext, Callbacks callbacks) {
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.scalarMappings = scalarMappings;
        this.configuration = configuration;
        this.policy = policy;
        this.manifestRecorder = manifestRecorder;
        this.typeModel = typeModel;
        this.applicationContext = applicationContext;
        this.callbacks = callbacks;
    }

    /**
     * @return what the binding found out about the input classes, an immutable snapshot of the input types bound so far
     */
    public InputClassChecks inputClassChecks() {
        return inputClassChecks.build();
    }

    private Supplier<ValueConverter> processInputObjectTypeDefinition(
            InputObjectTypeDefinition inputObjectTypeDefinition, Class<?> targetClass, MappingContext mappingContext) {
        String name = inputObjectTypeDefinition.getName();
        TypeFrame parent = current;
        callbacks.processIfNotProcessed(inputObjectTypeDefinition, targetClass, mappingContext, () -> {
            try {
                bindInputObjectType(inputObjectTypeDefinition, targetClass, mappingContext, parent);
            } catch (RuntimeException e) {
                inputFailures.put(name, e);
                throw e;
            }
        });
        RuntimeException failure = inputFailures.get(name);
        if (failure != null) {
            throw failure;
        }
        return () -> inputConverters.get(name);
    }

    private void bindInputObjectType(InputObjectTypeDefinition inputObjectTypeDefinition, Class<?> targetClass,
                                     MappingContext mappingContext, TypeFrame parent) {
        JClass inputClass = typeModel.of(targetClass);
        InputTypeRules.ClassProblem classProblem = InputTypeRules.classProblem(inputClass);
        if (classProblem == InputTypeRules.ClassProblem.NOT_A_CLASS) {
            throw IncorrectClassMappingException.forArgument(
                    IncorrectClassMappingException.MappingType.DETECT_TYPE,
                    IncorrectClassMappingException.MappingType.CUSTOM_CLASS,
                    mappingContext,
                    targetClass,
                    null
            );
        }
        if (classProblem == InputTypeRules.ClassProblem.NOT_INTROSPECTED) {
            throw new ClassNotIntrospectedException(
                    new TypeDefinitionMappingContext(inputObjectTypeDefinition, targetClass, mappingContext),
                    targetClass, GraphQLInput.class);
        }
        BeanIntrospection<Object> beanIntrospection = BeanIntrospector.SHARED
                .findIntrospection((Class<Object>) targetClass).orElseThrow();

        InputObjectConverter.Builder converter = InputObjectConverter.builder(beanIntrospection);
        ValueConverter custom = typeConverter(targetClass);
        Forward forward = new Forward();
        inputConverters.put(inputObjectTypeDefinition.getName(), forward);
        Set<String> mapped = new HashSet<>();
        Set<String> constructorArguments = Arrays.stream(beanIntrospection.getConstructorArguments())
                .map(Argument::getName)
                .collect(Collectors.toSet());

        TypeFrame frame = new TypeFrame(inputObjectTypeDefinition.getName(), parent);
        Binding binding = new Binding(frame, inputObjectTypeDefinition, targetClass, beanIntrospection, converter,
                forward, custom, mapped, constructorArguments, InputTypeRules.fields(inputClass));
        steps.push(new Step(frame, () -> bindField(binding, 0)));
    }

    private void bindField(Binding binding, int index) {
        InputObjectTypeDefinition inputObjectTypeDefinition = binding.definition();
        List<InputValueDefinition> inputValueDefinitions = inputObjectTypeDefinition.getInputValueDefinitions();
        if (index == inputValueDefinitions.size()) {
            finishInputObjectType(binding);
            return;
        }
        InputValueDefinition inputValueDefinition = inputValueDefinitions.get(index);
        Class<?> targetClass = binding.targetClass();
        BeanIntrospection<Object> beanIntrospection = binding.beanIntrospection();
        Optional<BeanProperty<Object, Object>> property = beanIntrospection.getProperty(inputValueDefinition.getName());

        if (binding.inputFields().binding(inputValueDefinition.getName()) == InputTypeRules.FieldBinding.MISSING) {
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

        // the steps of the next fields wait below the steps of this one, whose type is bound first
        steps.push(new Step(binding.frame(), () -> bindField(binding, index + 1)));
        Step bound = new Step(binding.frame(), null);
        steps.push(bound);
        Supplier<ValueConverter> fieldConverter = processInputType(inputValueDefinition.getType(),
                property.get().asArgument(), inputMappingContext, true);
        bound.action = () -> completeField(binding, inputValueDefinition, property.get(), inputMappingContext,
                fieldConverter);
    }

    private void completeField(Binding binding, InputValueDefinition inputValueDefinition,
                               BeanProperty<Object, Object> property, InputMappingContext inputMappingContext,
                               Supplier<ValueConverter> fieldConverter) {
        InputObjectTypeDefinition inputObjectTypeDefinition = binding.definition();
        manifestRecorder.inputField(inputObjectTypeDefinition.getName(), inputValueDefinition.getName(),
                inputValueDefinition.getType(), binding.beanIntrospection().getBeanType(), property);

        boolean argumentValue = ArgumentValue.class.equals(property.getType());
        if (binding.inputFields().binding(inputValueDefinition.getName()) == InputTypeRules.FieldBinding.NOT_SETTABLE) {
            if (argumentValue) {
                throw new UnsupportedInputTypeException(inputMappingContext, ArgumentValue.class.getName()
                        + " needs a writable property: it is set to omitted() when the client leaves it out.");
            }
            throw new InputFieldNotSettableException(inputMappingContext);
        }

        binding.converter().addField(inputValueDefinition.getName(), property, fieldConverter.get(), argumentValue);
        binding.mapped().add(property.getName());
    }

    private void finishInputObjectType(Binding binding) {
        InputObjectTypeDefinition inputObjectTypeDefinition = binding.definition();
        BeanIntrospection<Object> beanIntrospection = binding.beanIntrospection();
        // an ArgumentValue that this input type does not define, e.g. one that another input type backed by the same
        // class defines, is omitted
        for (BeanProperty<Object, Object> property : beanIntrospection.getBeanProperties()) {
            if (ArgumentValue.class.equals(property.getType()) && !binding.mapped().contains(property.getName())
                    && (!property.isReadOnly() || binding.constructorArguments().contains(property.getName()))) {
                binding.converter().addField(property.getName(), property, ValueConverters.IDENTITY, true);
            }
        }
        ValueConverter built = binding.custom() != null ? binding.custom() : binding.converter().build();
        binding.forward().target = built;
        inputConverters.put(inputObjectTypeDefinition.getName(), built);

        inputClassChecks.collect(inputObjectTypeDefinition,
                typeModel.of(beanIntrospection), beanIntrospection.getBeanType());

        manifestRecorder.type(inputObjectTypeDefinition.getName(), "input", binding.targetClass());
    }

    public ValueConverter processInputType(Type<?> graphQlType, Argument<?> argument, MappingContext mappingContext) {
        try {
            Supplier<ValueConverter> converter = processInputType(graphQlType, argument, mappingContext, true);
            run();
            return converter.get();
        } finally {
            steps.clear();
            current = null;
        }
    }

    private void run() {
        while (!steps.isEmpty()) {
            Step step = steps.pop();
            current = step.frame;
            try {
                step.action.run();
            } catch (RuntimeException e) {
                for (TypeFrame frame = step.frame; frame != null; frame = frame.parent) {
                    inputFailures.put(frame.name, e);
                }
                throw e;
            }
        }
        current = null;
    }

    private Supplier<ValueConverter> processInputType(Type<?> graphQlType, Argument<?> argument,
                                                      MappingContext mappingContext, boolean fieldLevel) {
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
                throw new UnsupportedInputTypeException(mappingContext,
                        InputTypeRules.unsupportedListReason(returnType.getName()));
            }

            Type<?> listFieldType = ((ListType) fieldType).getType();
            Argument<?> listArgument = argument.getFirstTypeVariable().get();

            Supplier<ValueConverter> element = processInputType(listFieldType, listArgument, mappingContext, false);
            return () -> ValueConverters.list(returnType, element.get());
        }

        TypeName typeName = requireTypeName(graphQlType);

        TypeDefinition<?> typeDefinition = typeDefinitionRegistry.getType(typeName).get();

        if (typeDefinition instanceof InputObjectTypeDefinition inputObjectTypeDefinition) {
            return processInputObjectTypeDefinition(
                    inputObjectTypeDefinition,
                    returnType,
                    mappingContext
            );
        } else if (typeDefinition instanceof EnumTypeDefinition enumTypeDefinition) {
            callbacks.processEnum(enumTypeDefinition, returnType, true, mappingContext);
            return () -> ValueConverters.IDENTITY;
        } else if (isGraphQlBuiltInType(typeName)) {
            manifestRecorder.scalar(typeName.getName());
            Set<Class<?>> supportedClasses = getSupportedClasses(typeName);

            if (!supportedClasses.contains(returnType)) {
                throw IncorrectClassMappingException.forArgument(mappingContext, returnType, supportedClasses);
            }
            if (ResolverClassRules.nullablePrimitive(typeModel.ref(argument), nonNull)
                    && policy.enabled(BindingCode.NULLABLE_PRIMITIVE)) {
                checkNullablePrimitive(mappingContext, returnType, typeName.getName(), supportedClasses);
            }
            ValueConverter builtIn = ValueConverters.builtIn(returnType);
            return () -> builtIn;
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
            ValueConverter scalar = ValueConverters.scalar(typeName.getName(), returnType, conversionService());
            return () -> scalar;
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
                        throw new InputConversionException("the input object is not accepted",
                                "The TypeConverter bean " + converter.getClass().getName()
                                        + " cannot convert the input object `" + value + "` to " + target.getName(),
                                null);
                    }
                    return converted.get();
                };
            }
        }
        return null;
    }

    private void checkNullablePrimitive(MappingContext mappingContext, Class<?> primitive, String scalarName,
                                        Set<Class<?>> supportedClasses) {
        String coordinate = mappingContext.getCoordinate();
        if (policy.allowed(BindingManifestRecorder.diagnosticLine(BindingCode.NULLABLE_PRIMITIVE, coordinate))) {
            return;
        }
        manifestRecorder.diagnostic(BindingCode.NULLABLE_PRIMITIVE, coordinate);
        IncorrectClassMappingException finding = IncorrectClassMappingException.forNullablePrimitiveInStrictMode(
                mappingContext, primitive, scalarName, supportedClasses);
        if (policy.failing(BindingCode.NULLABLE_PRIMITIVE)) {
            throw finding;
        }
        nullablePrimitiveWarnings.add(mappingContext.getMessage(IncorrectClassMappingException.nullablePrimitiveText(
                primitive, scalarName, supportedClasses)));
    }

    public void logNullablePrimitiveWarnings() {
        policy.warn(BindingCode.NULLABLE_PRIMITIVE, nullablePrimitiveWarnings);
    }

    private Argument<?> unwrapArgumentValue(Argument<?> argument, Type<?> graphQlType, boolean fieldLevel,
                                            MappingContext mappingContext) {
        if (!ArgumentValue.class.equals(argument.getType())) {
            return argument;
        }
        InputTypeRules.ArgumentValueMisuse misuse = InputTypeRules.argumentValueMisuse(typeModel.ref(argument), fieldLevel,
                graphQlType instanceof NonNullType);
        if (misuse != null) {
            throw new UnsupportedInputTypeException(mappingContext,
                    InputTypeRules.argumentValueReason(misuse, printType(graphQlType)));
        }
        return argument.getFirstTypeVariable().orElseThrow();
    }

    /**
     * What this phase needs from the type phase: a type is bound once, whichever side reaches it first, and an enum is
     * bound the same way for an argument and for a return type.
     */
    public interface Callbacks {

        void processIfNotProcessed(TypeDefinition<?> typeDefinition, Class<?> targetClass,
                                   MappingContext mappingContext, Runnable runnable);

        void processEnum(EnumTypeDefinition enumTypeDefinition, Class<?> targetClass, boolean input,
                         MappingContext mappingContext);
    }

    private static final class Step {

        private final TypeFrame frame;
        private Runnable action;

        private Step(TypeFrame frame, Runnable action) {
            this.frame = frame;
            this.action = action;
        }
    }

    /**
     * An input type that is being bound.
     *
     * @param name   the name of the type
     * @param parent the type that reached it, which fails when this one fails
     */
    private record TypeFrame(String name, TypeFrame parent) {
    }

    private record Binding(TypeFrame frame, InputObjectTypeDefinition definition, Class<?> targetClass,
                           BeanIntrospection<Object> beanIntrospection, InputObjectConverter.Builder converter,
                           Forward forward, ValueConverter custom, Set<String> mapped,
                           Set<String> constructorArguments, InputTypeRules.InputFields inputFields) {
    }

    /**
     * Stands for the converter of an input type while its fields are bound, so that a field that refers back to the type
     * has one to hold. Set once, before the first request; the converter itself is immutable.
     */
    private static final class Forward implements ValueConverter {

        private ValueConverter target;

        @Override
        @Nullable
        public Object convert(@Nullable Object value) {
            return target.convert(value);
        }
    }

}
