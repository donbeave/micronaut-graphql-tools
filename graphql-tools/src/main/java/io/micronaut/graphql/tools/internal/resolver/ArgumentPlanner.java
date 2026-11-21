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
package io.micronaut.graphql.tools.internal.resolver;

import graphql.language.InputValueDefinition;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.graphql.tools.mapping.TypeMappingContext;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict;
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules.SchemaArgument;
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules;
import io.micronaut.graphql.tools.internal.exceptions.configuration.ExecutionSetupException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.InjectedParameterException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.IncorrectArgumentCountException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.IncorrectArgumentNameException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.InvalidSourceArgumentException;
import io.micronaut.graphql.tools.internal.execution.DataLoaderTable;
import io.micronaut.graphql.tools.internal.execution.ExecutionContextAvailability;
import io.micronaut.graphql.tools.internal.input.InputTypeBinder;
import io.micronaut.graphql.tools.internal.input.ValueConverter;
import io.micronaut.graphql.tools.internal.model.RuntimeTypeModel;
import io.micronaut.inject.ExecutableMethod;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.micronaut.graphql.tools.internal.GraphQLUtils.getTypeName;
import static io.micronaut.graphql.tools.internal.MicronautUtils.getMethodName;

/**
 * Binds the parameters of a resolver method to the arguments of a field: {@link ResolverClassRules} decides, this class
 * binds each argument type and turns a violation into its exception.
 */
@Internal
public final class ArgumentPlanner {

    private final InputTypeBinder inputTypeBinder;
    private final RuntimeTypeModel typeModel;
    private final BeanContext beanContext;

    public ArgumentPlanner(InputTypeBinder inputTypeBinder, RuntimeTypeModel typeModel, BeanContext beanContext) {
        this.inputTypeBinder = inputTypeBinder;
        this.typeModel = typeModel;
        this.beanContext = beanContext;
    }

    public List<ArgumentDefinition> plan(Executable<?, ?> executable, @Nullable Class<?> sourceClass,
                                  TypeMappingContext mappingContext) {
        Argument<?>[] arguments = executable.getArguments();
        if (executable instanceof ExecutableMethod<?, ?> method && method.isSuspend()) {
            // the continuation is not a parameter of the resolver: the invocation supplies it
            arguments = Arrays.copyOf(arguments, arguments.length - 1);
        }
        List<InputValueDefinition> inputs = mappingContext.getFieldDefinition().getInputValueDefinitions();
        String sourceType = sourceClass == null ? null : sourceClass.getName();
        ArgumentVerdict verdict = ResolverClassRules.arguments(sourceType, typeModel.parameters(arguments),
                schemaArguments(inputs));
        if (verdict.failure() instanceof ArgumentVerdict.CountMismatch mismatch) {
            throw countMismatch(mismatch, getMethodName(executable), sourceType, mappingContext);
        }

        List<ArgumentDefinition> result = new ArrayList<>();
        for (ArgumentVerdict.Slot slot : verdict.slots()) {
            switch (slot.kind()) {
                case SOURCE, ENVIRONMENT, CONTEXT, SELECTION_SET, LOCALE -> result.add(ArgumentDefinition.ofKind(slot.kind()));
                case CONTEXT_VALUE -> result.add(ArgumentDefinition.ofContextValue(slot.argument()));
                case DATA_LOADER -> result.add(ArgumentDefinition.ofDataLoaderArgument(dataLoader(arguments[slot.parameter()],
                        executable, mappingContext)));
                case PRINCIPAL -> {
                    requireExecutionContext(arguments[slot.parameter()], executable, mappingContext, "the principal");
                    result.add(ArgumentDefinition.ofPrincipal(arguments[slot.parameter()].getType().asSubclass(Principal.class)));
                }
                case REQUEST -> {
                    requireExecutionContext(arguments[slot.parameter()], executable, mappingContext, "the HTTP request");
                    result.add(ArgumentDefinition.ofKind(slot.kind()));
                }
                case RESPONSE, OPTIONAL_RESPONSE -> {
                    requireExecutionContext(arguments[slot.parameter()], executable, mappingContext, "the HTTP response");
                    result.add(ArgumentDefinition.ofKind(slot.kind()));
                }
                default -> {
                    Argument<?> argument = arguments[slot.parameter()];
                    InputValueDefinition input = inputs.stream()
                            .filter(it -> it.getName().equals(slot.argument()))
                            .findFirst()
                            .get();
                    ValueConverter converter = inputTypeBinder.processInputType(input.getType(), argument,
                            TypeMappingContext.forArgument(mappingContext, slot.argument()));
                    result.add(ArgumentDefinition.ofInputValueArgument(slot.argument(), argument, converter));
                }
            }
        }

        if (verdict.failure() instanceof ArgumentVerdict.UntypedDataLoader untyped) {
            throw IncorrectArgumentNameException.untypedDataLoader(mappingContext, arguments[untyped.parameter()].getName());
        }
        if (verdict.failure() instanceof ArgumentVerdict.UnsatisfiableParameter unsatisfiable) {
            throw IncorrectArgumentNameException.unsatisfiableParameter(mappingContext,
                    arguments[unsatisfiable.parameter()].getName(), unsatisfiable.reason());
        }
        if (verdict.failure() instanceof ArgumentVerdict.InvalidSource invalid) {
            throw new InvalidSourceArgumentException(mappingContext, arguments[invalid.parameter()].getType(),
                    sourceClass);
        }
        if (verdict.failure() instanceof ArgumentVerdict.ParameterWithoutArgument without) {
            throw IncorrectArgumentNameException.parameterWithoutArgument(mappingContext,
                    arguments[without.parameter()].getName(), without.name(), without.available(), without.syntheticName());
        }
        if (verdict.failure() instanceof ArgumentVerdict.DuplicateParameter duplicate) {
            throw IncorrectArgumentNameException.duplicateParameter(mappingContext, duplicate.name());
        }
        return result;
    }

    /**
     * Resolves a {@code DataLoader<K, V>} parameter to the index of a loader bean, once at startup: the fetcher then reads
     * the loader by index and does no string lookup.
     */
    private int dataLoader(Argument<?> parameter, Executable<?, ?> executable, TypeMappingContext mappingContext) {
        String location = location(parameter, executable, mappingContext);
        try {
            ExecutionContextAvailability.require(beanContext, location, "a DataLoader");
            Argument<?>[] types = parameter.getTypeParameters();
            String named = parameter.getAnnotationMetadata().stringValue(AnnotationUtil.NAMED).orElse(null);
            return beanContext.getBean(DataLoaderTable.class).resolve(named, types[0], types[1], location);
        } catch (ExecutionSetupException e) {
            throw new InjectedParameterException(e.code(), e.getMessage(), mappingContext);
        }
    }

    /**
     * Fails the startup of a parameter that reads the per-execution state when the library's invocation, which creates it,
     * stepped aside.
     */
    private void requireExecutionContext(Argument<?> parameter, Executable<?, ?> executable,
                                         TypeMappingContext mappingContext, String need) {
        try {
            ExecutionContextAvailability.require(beanContext, location(parameter, executable, mappingContext), need);
        } catch (ExecutionSetupException e) {
            throw new InjectedParameterException(e.code(), e.getMessage(), mappingContext);
        }
    }

    private static String location(Argument<?> parameter, Executable<?, ?> executable, TypeMappingContext mappingContext) {
        return mappingContext.getGraphQlObjectType() + "." + mappingContext.getGraphQlField() + " (parameter '"
                + parameter.getName() + "' of " + executable.getDeclaringType().getName() + "#" + getMethodName(executable) + ")";
    }

    public void checkArgumentCount(BeanProperty<?, ?> beanProperty, TypeMappingContext mappingContext) {
        ArgumentVerdict verdict = ResolverClassRules.arguments(null, List.of(),
                schemaArguments(mappingContext.getFieldDefinition().getInputValueDefinitions()));
        if (verdict.failure() instanceof ArgumentVerdict.CountMismatch mismatch) {
            throw countMismatch(mismatch, beanProperty.getName(), null, mappingContext);
        }
    }

    private static IncorrectArgumentCountException countMismatch(ArgumentVerdict.CountMismatch mismatch,
                                                                 String methodName, @Nullable String sourceType,
                                                                 TypeMappingContext mappingContext) {
        List<SchemaArgument> suggested = mappingContext.getFieldDefinition().getInputValueDefinitions().stream()
                .map(it -> new SchemaArgument(it.getName(), getTypeName(it.getType()).getName()))
                .toList();
        return new IncorrectArgumentCountException(mappingContext, !mismatch.tooMany(), mismatch.found(),
                mismatch.required(), ResolverClassRules.suggestedMethod(methodName,
                mappingContext.getObjectTypeDefinition().getName(), sourceType, suggested));
    }

    private static List<String> schemaArguments(List<InputValueDefinition> inputs) {
        return inputs.stream().map(InputValueDefinition::getName).toList();
    }
}
