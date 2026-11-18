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

import graphql.language.InputValueDefinition;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict;
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules;
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules.SchemaArgument;
import io.micronaut.graphql.tools.exceptions.IncorrectArgumentCountException;
import io.micronaut.graphql.tools.exceptions.IncorrectArgumentNameException;
import io.micronaut.graphql.tools.exceptions.InvalidSourceArgumentException;
import io.micronaut.graphql.tools.schema.ValueConverter;

import java.util.ArrayList;
import java.util.List;

import static io.micronaut.graphql.tools.GraphQLUtils.getTypeName;
import static io.micronaut.graphql.tools.MicronautUtils.getMethodName;

/**
 * Binds the parameters of a resolver method to the arguments of a field: {@link ResolverClassRules} decides, this class
 * binds each argument type and turns a violation into its exception.
 */
@Internal
final class ArgumentPlanner {

    private final InputTypeBinder inputTypeBinder;
    private final RuntimeTypeModel typeModel;

    ArgumentPlanner(InputTypeBinder inputTypeBinder, RuntimeTypeModel typeModel) {
        this.inputTypeBinder = inputTypeBinder;
        this.typeModel = typeModel;
    }

    List<ArgumentDefinition> plan(Executable<?, ?> executable, @Nullable Class<?> sourceClass,
                                  TypeMappingContext mappingContext) {
        Argument<?>[] arguments = executable.getArguments();
        List<InputValueDefinition> inputs = mappingContext.getFieldDefinition().getInputValueDefinitions();
        String sourceType = sourceClass == null ? null : sourceClass.getName();
        ArgumentVerdict verdict = ResolverClassRules.arguments(sourceType, typeModel.parameters(arguments),
                schemaArguments(inputs));
        if (verdict.failure() instanceof ArgumentVerdict.CountMismatch mismatch) {
            throw countMismatch(mismatch, getMethodName(executable), sourceType, mappingContext);
        }

        List<ArgumentDefinition> result = new ArrayList<>();
        for (ArgumentVerdict.Slot slot : verdict.slots()) {
            if (slot.kind() == ArgumentVerdict.SlotKind.SOURCE) {
                result.add(ArgumentDefinition.ofSourceArgument());
            } else if (slot.kind() == ArgumentVerdict.SlotKind.ENVIRONMENT) {
                result.add(ArgumentDefinition.ofDataFetchingEnvironmentArgument());
            } else {
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

    void checkArgumentCount(BeanProperty<?, ?> beanProperty, TypeMappingContext mappingContext) {
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
