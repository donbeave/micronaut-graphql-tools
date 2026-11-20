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
package io.micronaut.graphql.tools.internal.execution;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.internal.exceptions.configuration.ExecutionSetupException;
import io.micronaut.graphql.tools.internal.exceptions.resolver.ExecuteOnException;
import io.micronaut.graphql.tools.mapping.MappingContext;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.executor.ExecutorSelector;

import java.util.concurrent.Executor;

/**
 * Reads {@code @ExecuteOn} of a field once at startup and looks the executor up by name through the
 * {@link ExecutorSelector}: no reflection and no annotation read per request. A field hops only when its executor
 * differs from the executor of the operation, which is the configured one while the library's invocation runs the
 * operations, and unknown when the application's own invocation replaced it (the field then always hops).
 */
@Internal
public final class FieldExecutors {

    private final ApplicationContext context;
    private final GraphQLToolsConfiguration configuration;
    private ExecutorSelector selector;
    private Executor requestExecutor;
    private boolean requestResolved;

    /**
     * @param context       the application context
     * @param configuration the library configuration
     */
    public FieldExecutors(ApplicationContext context, GraphQLToolsConfiguration configuration) {
        this.context = context;
        this.configuration = configuration;
    }

    /**
     * @param method   the annotation metadata of the method; it includes the class of a resolver
     * @param owner    the annotation metadata of the class that declares the method, or null
     * @param mappingContext the field that the method is bound to, for a failure
     * @return the executor to hop to, or null when the field does not hop
     * @throws ExecuteOnException with code {@code unknown-executor} when the name matches no executor bean
     */
    @Nullable
    public Executor forMethod(AnnotationMetadata method, @Nullable AnnotationMetadata owner, MappingContext mappingContext) {
        String name = method.stringValue(ExecuteOn.class).orElseGet(() -> owner == null ? null : owner.stringValue(ExecuteOn.class).orElse(null));
        if (name == null) {
            return null;
        }
        if (name.equals(TaskExecutors.SCHEDULED)) {
            throw new ExecuteOnException(BindingCode.UNKNOWN_EXECUTOR, "@ExecuteOn(\"scheduled\") names the executor of scheduled "
                    + "tasks, which does not run work. Use 'blocking', 'io', 'virtual' (JDK 21 and later) or an executor configured "
                    + "under micronaut.executors.", mappingContext);
        }
        Executor executor = selector().select(name).orElseThrow(() -> new ExecuteOnException(BindingCode.UNKNOWN_EXECUTOR,
                LibraryGraphQLInvocation.unknownExecutor("@ExecuteOn(\"" + name + "\")", name, context,
                        "Use one of them, or configure '" + name + "' under micronaut.executors."), mappingContext));
        return executor == requestExecutor() ? null : executor;
    }

    private ExecutorSelector selector() {
        if (selector == null) {
            selector = context.getBean(ExecutorSelector.class);
        }
        return selector;
    }

    @Nullable
    private Executor requestExecutor() {
        if (!requestResolved) {
            requestResolved = true;
            if (ExecutionContextAvailability.isAvailable(context)) {
                try {
                    requestExecutor = LibraryGraphQLInvocation.resolveExecutor(configuration.getExecution().getExecutor(), selector(), context);
                } catch (ExecutionSetupException e) {
                    // the invocation reports a key that names no executor; here the operation's executor is unknown
                    requestExecutor = null;
                }
            }
        }
        return requestExecutor;
    }
}
