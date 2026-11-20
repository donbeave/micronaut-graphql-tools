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
package io.micronaut.graphql.tools.internal.ws;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import io.micronaut.configuration.graphql.GraphQLConfiguration;
import io.micronaut.configuration.graphql.GraphQLInvocation;
import io.micronaut.configuration.graphql.GraphQLInvocationData;
import io.micronaut.configuration.graphql.ws.GraphQLWsConfiguration;
import io.micronaut.configuration.graphql.ws.GraphQLWsHandler;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.graphql.tools.internal.execution.ExecutionTransport;
import io.micronaut.graphql.tools.internal.execution.LibraryGraphQLInvocation;
import io.micronaut.graphql.tools.websocket.GraphQLConnectionInterceptor;
import io.micronaut.graphql.tools.websocket.GraphQLConnectionRequest;
import io.micronaut.graphql.tools.websocket.GraphQLConnectionResult;
import io.micronaut.http.HttpRequest;
import io.micronaut.json.JsonMapper;
import io.micronaut.scheduling.TaskScheduler;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The library's {@code graphql-transport-ws} handler. It replaces the one of {@code micronaut-graphql}, which on core
 * 4.10 fails to open a connection (its message parameter is an abstract class) and keeps its subscriptions in a map that
 * all sessions share. It is active under the transport's own switch, {@code graphql.graphql-ws.enabled}, and serves its
 * path.
 *
 * <p>The message arrives as a {@code String} and is read here, so no abstract type has to be instantiated. Everything the
 * connection needs is in a {@link WsSessionState} in the session's attributes: whether {@code connection_init} was
 * accepted, the attributes of the interceptors, and the operations by id, which only that connection can complete.
 * Every {@code subscribe} is one execution of the library's invocation with the WebSocket transport, the upgrade request,
 * no response and the connection attributes, so the context, the per-execution state and the DataLoader registry are
 * those of that operation. {@code complete} and the close of the connection cancel the operation.</p>
 *
 * <p>Close codes follow the protocol: 4400 invalid message, 4401 {@code subscribe} before the acknowledgement, 4403 a
 * rejection by an interceptor, 4408 no {@code connection_init} in time, 4409 an id that is already in use on the
 * connection and 4429 a second {@code connection_init}.</p>
 */
@Internal
@ServerWebSocket(
        value = "${" + GraphQLConfiguration.PREFIX + "." + GraphQLWsConfiguration.PATH_CONFIG + ":" + GraphQLWsConfiguration.DEFAULT_PATH + "}",
        subprotocols = "graphql-transport-ws"
)
@Requires(property = GraphQLWsConfiguration.ENABLED_CONFIG, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@Replaces(GraphQLWsHandler.class)
public class LibraryGraphQLWsHandler {

    private static final Logger LOG = LoggerFactory.getLogger(LibraryGraphQLWsHandler.class);
    private static final Argument<Map<String, Object>> OBJECT = Argument.mapOf(String.class, Object.class);
    private static final String GENERIC_FAILURE = "Internal server error.";

    private final GraphQLInvocation invocation;
    private final GraphQLWsConfiguration configuration;
    private final TaskScheduler scheduler;
    private final JsonMapper json;
    private final List<GraphQLConnectionInterceptor> interceptors;

    /**
     * @param invocation   the invocation of the application: the library's own, unless the application replaced it
     * @param configuration the configuration of the graphql-ws support
     * @param scheduler    schedules the connection initialisation timeout
     * @param json         reads and writes the messages
     * @param interceptors the connection interceptors, in order
     */
    public LibraryGraphQLWsHandler(GraphQLInvocation invocation, GraphQLWsConfiguration configuration, TaskScheduler scheduler,
                                   JsonMapper json, List<GraphQLConnectionInterceptor> interceptors) {
        this.invocation = invocation;
        this.configuration = configuration;
        this.scheduler = scheduler;
        this.json = json;
        this.interceptors = interceptors;
    }

    /**
     * @param session the new session
     * @param request the upgrade request
     */
    @OnOpen
    public void onOpen(WebSocketSession session, HttpRequest<?> request) {
        WsSessionState state = new WsSessionState(request);
        session.put(WsSessionState.KEY, state);
        state.initTimeout(scheduler.schedule(configuration.getConnectionInitWaitTimeout(), () -> {
            if (!state.acknowledged() && !state.closed()) {
                session.close(new CloseReason(4408, "Connection initialisation timeout."));
            }
        }));
    }

    /**
     * @param message the text of one message of the client
     * @param session the session
     */
    @OnMessage
    public void onMessage(String message, WebSocketSession session) {
        WsSessionState state = session.get(WsSessionState.KEY, WsSessionState.class).orElse(null);
        if (state == null) {
            session.close(CloseReason.INTERNAL_ERROR);
            return;
        }
        Map<String, Object> parsed;
        String type;
        try {
            parsed = json.readValue(message, OBJECT);
            type = parsed.get("type") instanceof String t ? t : null;
        } catch (IOException | RuntimeException e) {
            invalid(session);
            return;
        }
        if (type == null) {
            invalid(session);
            return;
        }
        switch (type) {
            case "connection_init" -> connectionInit(parsed, session, state);
            case "ping" -> send(session, Map.of("type", "pong")).subscribe();
            case "pong" -> { }
            case "subscribe" -> subscribe(parsed, session, state);
            case "complete" -> {
                if (parsed.get("id") instanceof String id) {
                    state.cancel(id);
                } else {
                    invalid(session);
                }
            }
            default -> invalid(session);
        }
    }

    /**
     * @param session the session that closed: every operation of it is cancelled and its state is released
     */
    @OnClose
    public void onClose(WebSocketSession session) {
        session.get(WsSessionState.KEY, WsSessionState.class).ifPresent(state -> {
            state.close();
            session.remove(WsSessionState.KEY);
        });
    }

    private void connectionInit(Map<String, Object> message, WebSocketSession session, WsSessionState state) {
        if (!state.firstInit()) {
            session.close(new CloseReason(4429, "Too many initialisation requests."));
            return;
        }
        state.cancelInitTimeout();
        Map<String, Object> payload = message.get("payload") instanceof Map<?, ?> map ? castMap(map) : Map.of();
        if (interceptors.isEmpty()) {
            acknowledge(session, state, GraphQLConnectionResult.accept());
            return;
        }
        GraphQLConnectionRequest request = new GraphQLConnectionRequest(payload, state.upgradeRequest());
        Runnable intercept = () -> acknowledge(session, state, intercept(request));
        Executor executor = invocation instanceof LibraryGraphQLInvocation library ? library.executor() : null;
        if (executor == null) {
            intercept.run();
            return;
        }
        try {
            executor.execute(intercept);
        } catch (RejectedExecutionException e) {
            LOG.warn("The executor rejected the interceptors of a connection: {}", e.toString());
            session.close(new CloseReason(1013, "Try again later."));
        }
    }

    private GraphQLConnectionResult intercept(GraphQLConnectionRequest request) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        try {
            for (GraphQLConnectionInterceptor interceptor : interceptors) {
                GraphQLConnectionResult result = interceptor.intercept(request);
                if (!result.isAccepted()) {
                    return result;
                }
                attributes.putAll(result.attributes());
            }
        } catch (RuntimeException e) {
            LOG.warn("A GraphQLConnectionInterceptor failed, so the connection is rejected: {}", e.toString());
            return GraphQLConnectionResult.reject();
        }
        return GraphQLConnectionResult.accept(attributes);
    }

    private void acknowledge(WebSocketSession session, WsSessionState state, GraphQLConnectionResult result) {
        if (state.closed()) {
            return;
        }
        if (!result.isAccepted()) {
            session.close(new CloseReason(result.closeCode(), result.reason()));
            return;
        }
        state.acknowledge(result.attributes());
        send(session, Map.of("type", "connection_ack")).subscribe();
    }

    @SuppressWarnings("unchecked")
    private void subscribe(Map<String, Object> message, WebSocketSession session, WsSessionState state) {
        if (!(message.get("id") instanceof String id) || id.isEmpty()
                || !(message.get("payload") instanceof Map<?, ?> payload) || !(payload.get("query") instanceof String query)) {
            invalid(session);
            return;
        }
        if (!state.acknowledged()) {
            session.close(new CloseReason(4401, "Unauthorized."));
            return;
        }
        Object operationName = payload.get("operationName");
        Object variables = payload.get("variables");
        GraphQLInvocationData data = new GraphQLInvocationData(query, operationName instanceof String name ? name : null,
                variables instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of());

        Disposable.Swap handle = Disposables.swap();
        if (!state.register(id, handle)) {
            session.close(new CloseReason(4409, "Subscriber for " + id + " already exists."));
            return;
        }
        AtomicBoolean failed = new AtomicBoolean();
        Mono<Void> operation = Flux.from(execute(data, state))
                .flatMap(result -> result.getData() instanceof Publisher<?> events ? asResults(events) : Flux.just(result))
                .concatMap(result -> result(session, id, result, failed))
                .then(Mono.defer(() -> failed.get() ? Mono.empty() : send(session, Map.of("id", id, "type", "complete"))));
        handle.update(operation
                .onErrorResume(failure -> {
                    LOG.warn("The operation '{}' of a WebSocket connection failed: {}", id, failure.toString());
                    return send(session, error(id, List.of(Map.of("message", GENERIC_FAILURE))));
                })
                .doFinally(signal -> state.finished(id, handle))
                .subscribe());
    }

    private Publisher<ExecutionResult> execute(GraphQLInvocationData data, WsSessionState state) {
        ConvertibleValues<Object> attributes = ConvertibleValues.of(state.attributes());
        if (invocation instanceof LibraryGraphQLInvocation library) {
            return library.execute(data, ExecutionTransport.WEBSOCKET, state.upgradeRequest(), null, attributes);
        }
        return invocation.invoke(data, state.upgradeRequest(), null);
    }

    @SuppressWarnings("unchecked")
    private static Flux<ExecutionResult> asResults(Publisher<?> events) {
        return Flux.from((Publisher<Object>) events).map(event -> {
            if (event instanceof ExecutionResult result) {
                return result;
            }
            throw new IllegalArgumentException("A subscription event is a " + event.getClass().getName() + ", not an ExecutionResult");
        });
    }

    private Mono<Void> result(WebSocketSession session, String id, ExecutionResult result, AtomicBoolean failed) {
        if (!result.isDataPresent()) {
            // the operation did not execute (it does not parse or validate): an error message ends it, as the protocol says
            failed.set(true);
            return send(session, error(id, result.getErrors().stream().map(GraphQLError::toSpecification).toList()));
        }
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("id", id);
        next.put("type", "next");
        next.put("payload", result.toSpecification());
        return send(session, next);
    }

    private static Map<String, Object> error(String id, List<?> errors) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", id);
        message.put("type", "error");
        message.put("payload", errors);
        return message;
    }

    private Mono<Void> send(WebSocketSession session, Map<String, Object> message) {
        if (!session.isOpen()) {
            return Mono.empty();
        }
        try {
            return Mono.from(session.send(json.writeValueAsString(message))).then();
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

    private static void invalid(WebSocketSession session) {
        session.close(new CloseReason(4400, "Invalid message."));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

}
