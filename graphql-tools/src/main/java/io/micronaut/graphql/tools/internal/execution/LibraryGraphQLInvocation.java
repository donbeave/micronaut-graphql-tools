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
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.customizer.GraphQLErrorsCustomizer;
import io.micronaut.graphql.tools.internal.LibraryBeanSelection;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration.ExecutionConfiguration;
import io.micronaut.graphql.tools.execution.DataLoaderBatchStatistics;
import io.micronaut.graphql.tools.execution.GraphQLContextContributor;
import io.micronaut.graphql.tools.execution.GraphQLTransport;
import io.micronaut.graphql.tools.internal.exceptions.configuration.ExecutionSetupException;
import io.micronaut.graphql.tools.internal.execution.disconnect.DisconnectWatcher;
import io.micronaut.graphql.tools.internal.execution.timeout.TimeoutScheduler;
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
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * The library's {@link GraphQLInvocation}: it replaces the transport's default one, which runs parsing, validation and
 * every synchronous resolver on the thread that receives the request (the Netty event loop). An operation hops once to
 * the configured executor ({@code micronaut.graphql.tools.execution.executor}, {@code blocking} by default) before
 * graphql-java runs, so that parsing, validation and every synchronous resolver run there. Nothing starts before the
 * returned publisher is subscribed. The application's {@link GraphQLExecutionInputCustomizer} publisher is subscribed on
 * the subscribing thread with the subscriber's Reactor context, and cancelling the returned publisher cancels that
 * subscription. With {@code none} the operation runs on the caller thread. The library does not
 * hop again at the execution strategy or per field.
 *
 * <p>The order: build the base {@link ExecutionInput} with the {@link ExecutionState} in its GraphQL
 * context and, when the application has batch loader beans, the lazy {@link LazyDataLoaderRegistry}; call the
 * application's customizer (skipped when it is the transport's default); put the state back when the customizer built a
 * fresh input; call the {@code GraphQLContextContributor} beans; run graphql-java. The propagated context is captured once on the calling thread and
 * installed in the hop only when the executing thread does not hold it already.
 * It is captured when {@link #execute} is called, not when the returned publisher is subscribed, so a subscriber on
 * another thread does not lose it.</p>
 *
 * <p>The bean is {@code @Secondary}: an application {@code GraphQLInvocation} of any kind (a bean without a qualifier,
 * a {@code @Primary} one, or one that declares {@code @Replaces(DefaultGraphQLInvocation.class)}) is the one an
 * unqualified injection point gets, and this bean stays registered beside it. It steps aside only when the
 * application declares a {@code DefaultGraphQLInvocation} bean through a factory, because it replaces that type.</p>
 */
@Internal
@Singleton
@Secondary
@Replaces(DefaultGraphQLInvocation.class)
@Requires(property = "graphql.enabled", notEquals = "false")
@Requires(condition = LibraryBeanSelection.NoApplicationDefaultInvocation.class)
public final class LibraryGraphQLInvocation implements GraphQLInvocation {

    private static final Logger LOG = LoggerFactory.getLogger(LibraryGraphQLInvocation.class);
    private static final String EXECUTOR_KEY = GraphQLToolsConfiguration.PREFIX + ".execution.executor";

    private final boolean selected;
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
    @Nullable
    private final GraphQLErrorsCustomizer[] errorsCustomizers;
    @Nullable
    private final DisconnectWatcher disconnectWatcher;
    @Nullable
    private final Duration timeout;
    @Nullable
    private final TimeoutScheduler scheduler;

    LibraryGraphQLInvocation(GraphQL graphQL, GraphQLExecutionInputCustomizer customizer, GraphQLToolsConfiguration configuration,
                             ExecutorSelector executorSelector, BeanContext beanContext, DataLoaderTable loaders,
                             List<GraphQLContextContributor> contributors,
                             List<GraphQLErrorsCustomizer> errorsCustomizers, @Nullable DisconnectWatcher disconnectWatcher) {
        this.disconnectWatcher = disconnectWatcher;
        this.errorsCustomizers = errorsCustomizers.isEmpty() ? null : errorsCustomizers.toArray(new GraphQLErrorsCustomizer[0]);
        this.graphQL = graphQL;
        this.customizer = customizer.getClass() == DefaultGraphQLExecutionInputCustomizer.class ? null : customizer;
        this.customizerName = this.customizer == null ? null : this.customizer.getClass().getName();
        // The setup that can fail on configuration (the registry bean, the executor, the timer) runs only when this bean
        // is the one that serves the transport. Beside an application invocation it is an unused member of a collection
        // of invocations, which must not fail, so it stays inert and fails fast if it is ever called.
        this.selected = ExecutionContextAvailability.isAvailable(beanContext);
        if (selected) {
            rejectRegistryBean(beanContext);
        }
        // no loader bean and no contributor, no registry: graphql-java keeps its empty registry and selects its no-op dispatch strategy
        this.loaders = loaders.isEmpty() ? null : loaders;
        this.contributors = contributors.isEmpty() ? null : contributors.toArray(new GraphQLContextContributor[0]);
        this.executorName = configuration.getExecution().getExecutor();
        this.executor = selected ? resolveExecutor(executorName, executorSelector, beanContext) : null;
        this.timeout = selected ? configuration.getExecution().getTimeout() : null;
        this.scheduler = timeout == null ? null : beanContext.getBean(TimeoutScheduler.class);
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
        if (!selected) {
            throw new IllegalStateException("The library's GraphQLInvocation is not the one that serves the transport: the application "
                    + "provides its own GraphQLInvocation, so this bean was not set up. Call the application's invocation instead.");
        }
        PropagatedContext propagated = PropagatedContext.getOrEmpty();
        if (request != null && propagated.find(ServerHttpRequestContext.class).isEmpty()) {
            propagated = propagated.plus(new ServerHttpRequestContext(request));
        }
        PropagatedContext captured = propagated;
        Mono<ExecutionResult> result = Mono.deferContextual(context -> {
            ExecutionState state = new ExecutionState(captured, transport, request, response, attributes, executor, customizerName);
            if (disconnectWatcher != null && request != null && transport == GraphQLTransport.HTTP) {
                state.disconnectWatcher(disconnectWatcher);
            }
            Execution execution = new Execution(data, state);
            if (scheduler != null) {
                ScheduledFuture<?> timer = scheduler.scheduleTimeout(execution::timeOutInContext, timeout.toNanos());
                execution.whenComplete((r, failure) -> timer.cancel(false));
            }
            if (customizer != null) {
                execution.startCustomized(context);
            } else if (executor == null) {
                execution.run();
            } else {
                try {
                    executor.execute(execution);
                } catch (RejectedExecutionException e) {
                    execution.reject(e);
                } catch (RuntimeException e) {
                    execution.completeExceptionally(e);
                }
            }
            return Mono.fromFuture(execution);
        });
        if (errorsCustomizers != null) {
            result = result.map(this::customizeErrors);
        }
        return result;
    }

    /**
     * @return the executor that runs the operations, or null when they run on the calling thread ({@code none})
     */
    @Nullable
    public Executor executor() {
        return executor;
    }

    /**
     * Applies the {@link GraphQLErrorsCustomizer} beans to one result; a subscription event is a response of its own.
     *
     * @param executionResult the result
     * @return the result, with the customized errors when there are customizers and the result has errors
     */
    public ExecutionResult customizeErrors(ExecutionResult executionResult) {
        if (errorsCustomizers == null) {
            return executionResult;
        }
        List<GraphQLError> errors = executionResult.getErrors();
        if (errors == null || errors.isEmpty()) {
            return executionResult;
        }
        List<GraphQLError> current = errors;
        for (GraphQLErrorsCustomizer customizer : errorsCustomizers) {
            try {
                List<GraphQLError> customized = customizer.customize(current);
                if (customized == null) {
                    throw new IllegalStateException("returned null instead of a list of errors");
                }
                current = customized;
                if (current.isEmpty()) {
                    break;
                }
            } catch (RuntimeException e) {
                LOG.error("The GraphQLErrorsCustomizer {} failed; the errors are sent as the previous customizer left them",
                        customizer.getClass().getName(), e);
            }
        }
        return current == errors ? executionResult : ExecutionResultImpl.newExecutionResult().from(executionResult).errors(current).build();
    }

    /**
     * @param name        the executor name from the configuration
     * @param selector    finds the executor
     * @param beanContext the bean context
     * @return the executor, or null for {@code none}; fails startup for a name that is unknown or reserved
     */
    public static ExecutorService resolveExecutor(String name, ExecutorSelector selector, BeanContext beanContext) {
        if (name.equals(ExecutionConfiguration.NO_EXECUTOR)) {
            return null;
        }
        if (name.equals(TaskExecutors.SCHEDULED)) {
            throw new ExecutionSetupException(BindingCode.UNKNOWN_EXECUTOR, EXECUTOR_KEY + " is 'scheduled', the executor of "
                    + "scheduled tasks, which does not run operations. Use 'blocking' (the default), 'io', 'virtual' (JDK 21 and "
                    + "later), an executor configured under micronaut.executors, or 'none' to run on the calling thread.");
        }
        return selector.select(name).orElseThrow(() -> new ExecutionSetupException(BindingCode.UNKNOWN_EXECUTOR,
                unknownExecutor(EXECUTOR_KEY + " is '" + name + "'", name, beanContext,
                        "Use one of them, or 'none' to run on the calling thread, or configure '" + name + "' under micronaut.executors.")));
    }

    /**
     * @param subject     what names the executor, for example the key with its value or an {@code @ExecuteOn} with its location
     * @param name        the executor name
     * @param beanContext the bean context
     * @param fix         the closing advice
     * @return the message of the failure, with the executor beans that exist
     */
    static String unknownExecutor(String subject, String name, BeanContext beanContext, String fix) {
        List<String> names = new ArrayList<>();
        beanContext.getBeanRegistrations(ExecutorService.class).forEach(registration -> names.add(registration.getIdentifier().getName()));
        names.sort(null);
        StringBuilder message = new StringBuilder(subject).append(", which names no executor bean. ");
        if (name.equals(TaskExecutors.VIRTUAL) && Runtime.version().feature() < 21) {
            message.append("The 'virtual' executor needs JDK 21 or later, and this JVM is JDK ").append(Runtime.version().feature())
                    .append("; use 'blocking', which is the io pool on JDK 17 and virtual threads on JDK 21 and later. ");
        }
        message.append("The executor beans are: ").append(String.join(", ", names)).append(". ").append(fix);
        return message.toString();
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

    private static ExecutionResult cancelled() {
        return ExecutionResultImpl.newExecutionResult()
                .data(null)
                .addError(GraphqlErrorBuilder.newError()
                        .message("The operation was cancelled before it started.")
                        .extensions(Map.of("code", "EXECUTION_CANCELLED"))
                        .build())
                .build();
    }

    private static ExecutionResult timedOut(Duration timeout) {
        return ExecutionResultImpl.newExecutionResult()
                .data(null)
                .addError(GraphqlErrorBuilder.newError()
                        .message("The operation did not finish within " + timeout + " and was cancelled.")
                        .extensions(Map.of("code", "EXECUTION_TIMEOUT"))
                        .build())
                .build();
    }

    /**
     * One operation: the task of the hop, the receiver of graphql-java's result and the future of the result. Cancelling
     * the future, which the cancellation of the response subscription does, marks the execution cancelled.
     */
    private final class Execution extends CompletableFuture<ExecutionResult> implements Runnable, BiConsumer<ExecutionResult, Throwable> {

        private final GraphQLInvocationData data;
        private final ExecutionState state;
        private volatile Disposable customizerSubscription;

        Execution(GraphQLInvocationData data, ExecutionState state) {
            this.data = data;
            this.state = state;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            // the subscriber cancels after it has the result too, and a result that is done has nothing left to stop
            boolean live = !isDone();
            if (live) {
                state.cancel();
                // a pending stage may never complete, so nothing else would stop the watch of the connection
                state.finished();
            }
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (live) {
                disposeCustomizer();
            }
            return cancelled;
        }

        /**
         * Disposes the customizer subscription if it is stored. The caller has already made this future done, and
         * {@link #startCustomized} checks that after it stores the subscription, so whichever of the two comes second
         * disposes it, and a customizer that never completes is not left subscribed.
         */
        private void disposeCustomizer() {
            Disposable subscription = customizerSubscription;
            if (subscription != null) {
                subscription.dispose();
            }
        }

        void timeOutInContext() {
            inContext(this::timeOut);
        }

        /** The timer of {@code execution.timeout} fired: cancel the execution and answer the client now. */
        void timeOut() {
            // the answer first: cancelling the state aborts the pending stages, and the execution would finish with their partial data
            if (!complete(timedOut(timeout))) {
                return;
            }
            state.cancel();
            state.finished();
            disposeCustomizer();
        }

        @Override
        public void run() {
            if (state.isCancelled()) {
                complete(cancelled());
                return;
            }
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
                executeAsync(baseInput());
            } catch (Throwable e) {
                completeExceptionally(e);
            }
        }

        private ExecutionInput baseInput() {
            ExecutionInput.Builder builder = ExecutionInput.newExecutionInput()
                    .query(data.getQuery())
                    .operationName(data.getOperationName())
                    .variables(data.getVariables());
            BatchCounters counters = null;
            if (loaders != null) {
                counters = loaders.statistics() ? new BatchCounters() : null;
                builder.dataLoaderRegistry(new LazyDataLoaderRegistry(loaders, state, counters));
            } else if (contributors != null) {
                // graphql-java's own empty registry is immutable, so a contributor could not register a loader in it
                builder.dataLoaderRegistry(new DataLoaderRegistry());
            }
            ExecutionInput input = builder.build();
            state.storeIn(input.getGraphQLContext());
            if (counters != null) {
                input.getGraphQLContext().put(DataLoaderBatchStatistics.CONTEXT_KEY, counters);
            }
            return input;
        }

        /** Subscribes the customizer on the subscribing thread, with the subscriber's context; the rest hops. */
        @SuppressWarnings("unchecked")
        void startCustomized(ContextView context) {
            try {
                ExecutionInput input = baseInput();
                Disposable subscription = Mono.from(customizer.customize(input, state.getHttpRequest(), (MutableHttpResponse<String>) state.response()))
                        .switchIfEmpty(Mono.error(() -> new IllegalStateException("The GraphQLExecutionInputCustomizer "
                                + customizer.getClass().getName() + " published no ExecutionInput")))
                        .contextWrite(context)
                        .subscribe(this::customized, this::completeExceptionally);
                customizerSubscription = subscription;
                if (isDone()) {
                    subscription.dispose();
                }
            } catch (Throwable e) {
                completeExceptionally(e);
            }
        }

        private void customized(ExecutionInput customized) {
            if (executor != null) {
                try {
                    executor.execute(() -> inContext(() -> executeCustomized(customized)));
                } catch (RejectedExecutionException e) {
                    reject(e);
                } catch (RuntimeException e) {
                    completeExceptionally(e);
                }
            } else {
                inContext(() -> executeCustomized(customized));
            }
        }

        private void executeCustomized(ExecutionInput customized) {
            if (state.isCancelled()) {
                complete(cancelled());
                return;
            }
            try {
                if (!state.isStoredIn(customized.getGraphQLContext())) {
                    state.storeIn(customized.getGraphQLContext());
                }
                executeAsync(customized);
            } catch (Throwable e) {
                completeExceptionally(e);
            }
        }

        private void executeAsync(ExecutionInput input) {
            state.executionInput(input);
            if (contribute(input.getGraphQLContext())) {
                if (state.isCancelled()) {
                    // the timeout fired while the contributors ran: the client has its answer, so no resolver starts
                    complete(cancelled());
                    return;
                }
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
                        complete(ExecutionResultImpl.newExecutionResult().data(null).addError(error).build());
                    } else {
                        LOG.error("The GraphQLContextContributor {} failed; the operation is answered with an error",
                                contributor.getClass().getName(), e);
                        complete(contributorFailed());
                    }
                    return false;
                }
            }
            return true;
        }

        @Override
        public void accept(ExecutionResult executionResult, Throwable failure) {
            state.finished();
            if (failure != null) {
                completeExceptionally(failure);
            } else {
                complete(executionResult);
            }
        }

        void reject(RejectedExecutionException e) {
            LOG.warn("The executor '{}' rejected an operation, which is answered with EXECUTION_REJECTED: {}", executorName, e.toString());
            complete(rejected());
        }
    }

}
