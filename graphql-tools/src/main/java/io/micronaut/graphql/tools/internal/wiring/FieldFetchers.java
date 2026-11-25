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

import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.idl.TypeRuntimeWiring;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Executable;
import io.micronaut.core.type.ReturnType;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames;
import io.micronaut.graphql.tools.binding.internal.rules.ReturnVerdict;
import io.micronaut.graphql.tools.execution.FieldDescriptor;
import io.micronaut.graphql.tools.internal.error.LibraryDataFetcherExceptionHandler;
import io.micronaut.graphql.tools.internal.exceptions.configuration.ExecutionSetupException;
import io.micronaut.graphql.tools.internal.execution.FieldExecutionPlanner;
import io.micronaut.graphql.tools.internal.execution.SuspendInvoker;
import io.micronaut.graphql.tools.internal.resolver.ArgumentDefinition;
import io.micronaut.graphql.tools.internal.resolver.FlowAdapter;
import io.micronaut.graphql.tools.internal.resolver.FlowDataFetcher;
import io.micronaut.graphql.tools.internal.resolver.MicronautExecutableMethodDataFetcher;
import io.micronaut.graphql.tools.internal.resolver.ReturnTypes;
import io.micronaut.graphql.tools.mapping.TypeMappingContext;
import io.micronaut.inject.ExecutableMethod;
import java.util.List;
import java.util.concurrent.Executor;

import static io.micronaut.graphql.tools.internal.MicronautUtils.getExecutableMethodFullName;

/** Builds the data fetcher of a method field: arguments, suspend and Flow adapters, executor hop and decorators. */
@Internal
public final class FieldFetchers {

    private final WiringState s;
    private final TypeBinder types;

    FieldFetchers(WiringState s, TypeBinder types) {
        this.s = s;
        this.types = types;
    }

    void processExecutableMethod(Executable<Object, ?> executable, ReturnType<?> returnType,
                                 @Nullable Class<?> sourceClass, @Nullable Object instance,
                                 TypeRuntimeWiring.Builder typeRuntimeWiringBuilder,
                                 TypeMappingContext mappingContext, @Nullable AnnotationMetadata owner) {
        mappingContext = TypeMappingContext.forField(
                mappingContext,
                executable.getDeclaringType(),
                getExecutableMethodFullName(executable)
        );

        SuspendInvoker suspendInvoker = suspendInvoker(executable, mappingContext);

        List<ArgumentDefinition> argumentDefinitions = s.argumentPlanner.plan(executable, sourceClass,
                mappingContext);

        ReturnVerdict verdict = types.processFieldReturnType(returnType.asArgument(),
                mappingContext.getFieldDefinition().getType(), mappingContext, true, isSingleResult(executable),
                suspendInvoker != null);

        // decided once per field here: a field that is not async and has no @ExecuteOn keeps the plain method fetcher and allocates nothing more
        Executor fieldExecutor = s.fieldExecutors.forMethod(executable.getAnnotationMetadata(), owner, mappingContext);
        DataFetcher<Object> fetcher = new MicronautExecutableMethodDataFetcher(executable, argumentDefinitions, instance,
                suspendInvoker);
        boolean subscriptionField = mappingContext.getGraphQlObjectType().equals(s.subscriptionTypeName);
        if (subscriptionField && ReturnTypes.isFlow(returnType.getType())) {
            fetcher = new FlowDataFetcher(fetcher, flowAdapter(s.context, executable,
                    mappingContext.getGraphQlObjectType() + "." + mappingContext.getGraphQlField()));
        }
        DataFetcher<Object> planned = FieldExecutionPlanner.plan(fetcher, verdict, subscriptionField,
                s.context.getConversionService(), fieldExecutor,
                s.context.findBean(LibraryDataFetcherExceptionHandler.class).orElse(null));
        typeRuntimeWiringBuilder.dataFetcher(
                mappingContext.getFieldDefinition().getName(),
                s.fieldDecorators.apply(new FieldDescriptor(coordinates(mappingContext),
                        FieldExecutionPlanner.kind(verdict, subscriptionField), hasArguments(mappingContext), fieldExecutor,
                        executable.getAnnotationMetadata(),
                        executable.getDeclaringType().getName() + " " + getExecutableMethodFullName(executable)), planned)
        );
    }

    static FieldCoordinates coordinates(TypeMappingContext mappingContext) {
        return FieldCoordinates.coordinates(mappingContext.getGraphQlObjectType(), mappingContext.getGraphQlField());
    }

    static boolean hasArguments(TypeMappingContext mappingContext) {
        return !mappingContext.getFieldDefinition().getInputValueDefinitions().isEmpty();
    }

    /**
     * @param executable     the method that the field binds
     * @param mappingContext the field, for the failure
     * @return the invoker of a Kotlin {@code suspend} method, null for any other method
     * @throws ExecutionSetupException with code {@code suspend-without-coroutines} when the invoker does not exist
     */
    @Nullable
    SuspendInvoker suspendInvoker(Executable<Object, ?> executable, TypeMappingContext mappingContext) {
        return suspendInvoker(s.context, executable,
                mappingContext.getGraphQlObjectType() + "." + mappingContext.getGraphQlField());
    }

    public static SuspendInvoker suspendInvoker(BeanContext context, Executable<Object, ?> executable, String coordinate) {
        if (!(executable instanceof ExecutableMethod<?, ?> method && method.isSuspend())) {
            return null;
        }
        return context.findBean(SuspendInvoker.class).orElseThrow(() -> new ExecutionSetupException(
                BindingCode.SUSPEND_WITHOUT_COROUTINES, "The field " + coordinate + " binds the Kotlin suspend function "
                + getExecutableMethodFullName(executable) + ", which needs kotlinx-coroutines-core on the classpath, and it "
                + "is not there. Fix: add org.jetbrains.kotlinx:kotlinx-coroutines-core to the dependencies of the application."));
    }

    public static FlowAdapter flowAdapter(BeanContext context, Executable<Object, ?> executable, String coordinate) {
        return context.findBean(FlowAdapter.class).orElseThrow(() -> new ExecutionSetupException(
                BindingCode.FLOW_WITHOUT_REACTIVE_COROUTINES, "The field " + coordinate + " binds the Kotlin Flow method "
                + getExecutableMethodFullName(executable) + ", which needs kotlinx-coroutines-reactive on the classpath, and "
                + "it is not there. Fix: add org.jetbrains.kotlinx:kotlinx-coroutines-reactive to the dependencies of the application."));
    }

    static boolean isSingleResult(AnnotationMetadataProvider method) {
        return method.getAnnotationMetadata().hasStereotype(AnnotationNames.SINGLE_RESULT);
    }
}
