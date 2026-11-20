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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import io.micronaut.configuration.graphql.DefaultGraphQLExecutionInputCustomizer;
import io.micronaut.configuration.graphql.DefaultGraphQLInvocation;
import io.micronaut.configuration.graphql.GraphQLExecutionInputCustomizer;
import io.micronaut.configuration.graphql.GraphQLInvocation;
import io.micronaut.configuration.graphql.GraphQLInvocationData;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration.ExecutionConfiguration;
import io.micronaut.graphql.tools.internal.LibraryBeanSelection;
import io.micronaut.graphql.tools.execution.GraphQLContextContributor;
import io.micronaut.graphql.tools.execution.GraphQLTransport;
import io.micronaut.graphql.tools.internal.exceptions.configuration.ExecutionSetupException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.context.ServerHttpRequestContext;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.executor.ExecutorSelector;
import jakarta.inject.Singleton;
import org.dataloader.DataLoaderRegistry;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;

/**
 * The library's {@link GraphQLInvocation}: it replaces the transport's default one, which runs parsing, validation and
 * every synchronous resolver on the thread that receives the request (the Netty event loop). An operation hops once to
 * the configured executor ({@code micronaut.graphql.tools.execution.executor}, {@code blocking} by default) before
 * graphql-java runs, so that parsing, validation, the application's {@link GraphQLExecutionInputCustomizer} and every
 * synchronous resolver run there. With {@code none} the operation runs on the caller thread. The library does not
 * hop again at the execution strategy or per field.
 *
 * <p>The order after the hop: build the base {@link ExecutionInput} with the {@link ExecutionState} in its GraphQL
 * context; call the application's customizer (skipped when it is the transport's default); put the state back when the
 * customizer built a fresh input; run graphql-java. The propagated context is captured once on the calling thread and
 * installed in the hop only when the executing thread does not hold it already.</p>
 *
 * <p>The bean steps aside when the application declares a {@code GraphQLInvocation} that is {@code @Primary} or
 * {@code @Replaces(DefaultGraphQLInvocation.class)}.</p>
 */
@Internal
@Singleton
@Replaces(DefaultGraphQLInvocation.class)
@Requires(property = "graphql.enabled", notEquals = "false")
@Requires(condition = LibraryBeanSelection.NoCompetingInvocation.class)
public final class LibraryGraphQLInvocation implements GraphQLInvocation {

    private static final Logger LOG = LoggerFactory.getLogger(LibraryGraphQLInvocation.class);
    private static final String EXECUTOR_KEY = GraphQLToolsConfiguration.PREFIX + ".execution.executor";

    private final GraphQL graphQL;
    @Nullable
    private final GraphQLExecutionInputCustomizer customizer;
    @Nullable
    private final DataLoaderTable loaders;
    @Nullable
    private final GraphQLContextContributor[] contributors;
    @Nullable
    private final String customizerName;
    @Nullable
    private final ExecutorService executor;
    private final String executorName;

    LibraryGraphQLInvocation(GraphQL graphQL, GraphQLExecutionInputCustomizer customizer, GraphQLToolsConfiguration configuration,
                             ExecutorSelector executorSelector, BeanContext beanContext, DataLoaderTable loaders,
                             List<GraphQLContextContributor> contributors) {
        this.graphQL = graphQL;
        this.customizer = customizer.getClass() == DefaultGraphQLExecutionInputCustomizer.class ? null : customizer;
        this.customizerName = this.customizer == null ? null : this.customizer.getClass().getName();
        rejectRegistryBean(beanContext);
        // no loader bean, no registry: graphql-java keeps its empty registry and selects its no-op dispatch strategy
        this.loaders = loaders.isEmpty() ? null : loaders;
        this.contributors = contributors.isEmpty() ? null : contributors.toArray(new GraphQLContextContributor[0]);
        this.executorName = configuration.getExecution().getExecutor();
        this.executor = resolveExecutor(executorName, executorSelector, beanContext);
    }

    /**
     * @return the name of the executor that runs the operations, as configured
     */
    public String executorName() {
        return executorName;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Publisher<ExecutionResult> invoke(GraphQLInvocationData data, HttpRequest request, MutableHttpResponse<String> response) {
        return execute(data, GraphQLTransport.HTTP, request, response, ConvertibleValues.empty());
    }

    /**
     * Runs one operation: the entry point of the transports. {@link #invoke} delegates to it for HTTP; the WebSocket
     * handler calls it with its own transport, the upgrade request, no response and the connection attributes.
     *
     * @param data       the query, operation name and variables
     * @param transport  how the operation arrived
     * @param request    the HTTP request or the upgrade request, or null
     * @param response   the HTTP response, or null when the transport has none
     * @param attributes the connection attributes of a WebSocket connection, else empty
     * @return a publisher of the one result
     */
    public Publisher<ExecutionResult> execute(GraphQLInvocationData data, GraphQLTransport transport,
                                              @Nullable HttpRequest<?> request, @Nullable MutableHttpResponse<?> response,
                                              ConvertibleValues<Object> attributes) {
        PropagatedContext captured = PropagatedContext.getOrEmpty();
        if (request != null && captured.find(ServerHttpRequestContext.class).isEmpty()) {
            captured = captured.plus(new ServerHttpRequestContext(request));
        }
        Execution execution = new Execution(data, new ExecutionState(captured, transport, request, response, attributes, customizerName));
        if (executor == null) {
            execution.run();
        } else {
            try {
                executor.execute(execution);
            } catch (RejectedExecutionException e) {
                execution.reject(e);
            }
        }
        return Mono.fromFuture(execution.result);
    }

    private static ExecutorService resolveExecutor(String name, ExecutorSelector selector, BeanContext beanContext) {
        if (name.equals(ExecutionConfiguration.NO_EXECUTOR)) {
            return null;
        }
        if (name.equals(TaskExecutors.SCHEDULED)) {
            throw new ExecutionSetupException(BindingCode.UNKNOWN_EXECUTOR, EXECUTOR_KEY + " is 'scheduled', the executor of "
                    + "scheduled tasks, which does not run operations. Use 'blocking' (the default), 'io', 'virtual' (JDK 21 and "
                    + "later), an executor configured under micronaut.executors, or 'none' to run on the calling thread.");
        }
        return selector.select(name).orElseThrow(() -> unknownExecutor(name, beanContext));
    }

    private static ExecutionSetupException unknownExecutor(String name, BeanContext beanContext) {
        List<String> names = new ArrayList<>();
        beanContext.getBeanRegistrations(ExecutorService.class).forEach(registration -> names.add(registration.getIdentifier().getName()));
        names.sort(null);
        StringBuilder message = new StringBuilder(EXECUTOR_KEY).append(" is '").append(name).append("', which names no executor bean. ");
        if (name.equals(TaskExecutors.VIRTUAL) && Runtime.version().feature() < 21) {
            message.append("The 'virtual' executor needs JDK 21 or later, and this JVM is JDK ").append(Runtime.version().feature())
                    .append("; use 'blocking', which is the io pool on JDK 17 and virtual threads on JDK 21 and later. ");
        }
        message.append("The executor beans are: ").append(String.join(", ", names)).append(". Use one of them, or 'none' to run on the "
                + "calling thread, or configure '").append(name).append("' under micronaut.executors.");
        return new ExecutionSetupException(BindingCode.UNKNOWN_EXECUTOR, message.toString());
    }

    private static void rejectRegistryBean(BeanContext beanContext) {
        List<String> beans = new ArrayList<>();
        beanContext.getBeanDefinitions(DataLoaderRegistry.class).forEach(definition -> beans.add(definition.getDeclaringType().orElse(definition.getBeanType()).getName()));
        if (!beans.isEmpty()) {
            beans.sort(null);
            throw new ExecutionSetupException(BindingCode.DATA_LOADER_REGISTRY_BEAN, "The application has a DataLoaderRegistry bean ("
                    + String.join(", ", beans) + "). The library's invocation does not use it: its loaders would be shared across "
                    + "operations on one WebSocket connection. Fix: register the loaders as BatchLoader beans (BatchLoader, "
                    + "BatchLoaderWithContext, MappedBatchLoader or MappedBatchLoaderWithContext), add them in a "
                    + "GraphQLContextContributor through the registry of the ExecutionInput, or provide your own GraphQLInvocation "
                    + "with @Replaces(DefaultGraphQLInvocation.class).");
        }
    }

    private static ExecutionResult contributorFailed() {
        return ExecutionResultImpl.newExecutionResult()
                .data(null)
                .addError(GraphqlErrorBuilder.newError()
                        .message("The operation could not be prepared: an internal error occurred.")
                        .extensions(Map.of("code", "INTERNAL_ERROR"))
                        .build())
                .build();
    }

    private static ExecutionResult rejected() {
        return ExecutionResultImpl.newExecutionResult()
                .data(null)
                .addError(GraphqlErrorBuilder.newError()
                        .message("The server could not accept the request: its executor rejected it.")
                        .extensions(Map.of("code", "EXECUTION_REJECTED"))
                        .build())
                .build();
    }

    /** One operation: the task of the hop, and the receiver of graphql-java's result. */
    private final class Execution implements Runnable, BiConsumer<ExecutionResult, Throwable> {

        private final GraphQLInvocationData data;
        private final ExecutionState state;
        private final CompletableFuture<ExecutionResult> result = new CompletableFuture<>();
        private Thread hopThread;

        Execution(GraphQLInvocationData data, ExecutionState state) {
            this.data = data;
            this.state = state;
        }

        @Override
        public void run() {
            inContext(this::start);
        }

        private void inContext(Runnable body) {
            PropagatedContext captured = state.propagatedContext();
            if (PropagatedContext.getOrEmpty() == captured) {
                body.run();
                return;
            }
            try (PropagatedContext.Scope ignored = captured.propagate()) {
                body.run();
            }
        }

        private void start() {
            try {
                ExecutionInput.Builder builder = ExecutionInput.newExecutionInput()
                        .query(data.getQuery())
                        .operationName(data.getOperationName())
                        .variables(data.getVariables());
                if (loaders != null) {
                    builder.dataLoaderRegistry(new LazyDataLoaderRegistry(loaders, state));
                }
                ExecutionInput input = builder.build();
                state.storeIn(input.getGraphQLContext());
                if (customizer == null) {
                    executeAsync(input);
                } else {
                    customize(input);
                }
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        }

        @SuppressWarnings("unchecked")
        private void customize(ExecutionInput input) {
            hopThread = Thread.currentThread();
            Mono.from(customizer.customize(input, state.httpRequest(), (MutableHttpResponse<String>) state.response()))
                    .switchIfEmpty(Mono.error(() -> new IllegalStateException("The GraphQLExecutionInputCustomizer "
                            + customizer.getClass().getName() + " published no ExecutionInput")))
                    .subscribe(this::customized, result::completeExceptionally);
        }

        private void customized(ExecutionInput customized) {
            if (executor != null && Thread.currentThread() != hopThread) {
                // a customizer that answers on another thread (for example an event loop) must not carry the operation along
                try {
                    executor.execute(() -> inContext(() -> executeCustomized(customized)));
                } catch (RejectedExecutionException e) {
                    reject(e);
                }
            } else {
                executeCustomized(customized);
            }
        }

        private void executeCustomized(ExecutionInput customized) {
            try {
                if (!state.isStoredIn(customized.getGraphQLContext())) {
                    state.storeIn(customized.getGraphQLContext());
                }
                executeAsync(customized);
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        }

        private void executeAsync(ExecutionInput input) {
            state.executionInput(input);
            if (contribute(input.getGraphQLContext())) {
                graphQL.executeAsync(input).whenComplete(this);
            }
        }

        /** Runs the contributors, in order; false when one failed and the result is already answered. */
        private boolean contribute(GraphQLContext context) {
            if (contributors == null) {
                return true;
            }
            for (GraphQLContextContributor contributor : contributors) {
                try {
                    contributor.contribute(context, state);
                } catch (Exception e) {
                    if (e instanceof GraphQLError error) {
                        result.complete(ExecutionResultImpl.newExecutionResult().data(null).addError(error).build());
                    } else {
                        LOG.error("The GraphQLContextContributor {} failed; the operation is answered with an error",
                                contributor.getClass().getName(), e);
                        result.complete(contributorFailed());
                    }
                    return false;
                }
            }
            return true;
        }

        @Override
        public void accept(ExecutionResult executionResult, Throwable failure) {
            if (failure != null) {
                result.completeExceptionally(failure);
            } else {
                result.complete(executionResult);
            }
        }

        void reject(RejectedExecutionException e) {
            LOG.warn("The executor '{}' rejected an operation, which is answered with EXECUTION_REJECTED: {}", executorName, e.toString());
            result.complete(rejected());
        }
    }

}
