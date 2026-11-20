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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import reactor.core.Disposable;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Everything the handler keeps for one WebSocket connection, in the attributes of its {@code WebSocketSession}: whether
 * {@code connection_init} arrived and was accepted, the attributes the interceptors returned, and the running operations by
 * id. One object per connection and no field of it is read by a resolver, so a field costs nothing. {@link #close()}
 * disposes every operation and releases the rest, so nothing outlives the connection, and an operation is found only
 * through the session of the client that started it.
 */
@Internal
final class WsSessionState {

    static final String KEY = "graphql-tools.ws-session-state";

    private final HttpRequest<?> upgradeRequest;
    private final AtomicBoolean initReceived = new AtomicBoolean();
    private final ConcurrentHashMap<String, Disposable> operations = new ConcurrentHashMap<>();
    private volatile boolean acknowledged;
    private volatile boolean closed;
    private volatile Map<String, Object> attributes = Collections.emptyMap();
    @Nullable
    private volatile Future<?> initTimeout;

    WsSessionState(HttpRequest<?> upgradeRequest) {
        this.upgradeRequest = upgradeRequest;
    }

    HttpRequest<?> upgradeRequest() {
        return upgradeRequest;
    }

    /** @return true for the first {@code connection_init} of the connection, false for every later one */
    boolean firstInit() {
        return initReceived.compareAndSet(false, true);
    }

    void initTimeout(Future<?> timeout) {
        initTimeout = timeout;
        if (closed) {
            timeout.cancel(false);
        }
    }

    void cancelInitTimeout() {
        Future<?> timeout = initTimeout;
        if (timeout != null) {
            timeout.cancel(false);
        }
    }

    void acknowledge(Map<String, Object> connectionAttributes) {
        attributes = connectionAttributes;
        acknowledged = true;
    }

    boolean acknowledged() {
        return acknowledged;
    }

    Map<String, Object> attributes() {
        return attributes;
    }

    /**
     * @param id     the operation id
     * @param handle cancels the operation
     * @return false when an operation with this id already runs on this connection; when the connection is closed, the
     * handle is disposed
     */
    boolean register(String id, Disposable handle) {
        if (operations.putIfAbsent(id, handle) != null) {
            return false;
        }
        if (closed) {
            operations.remove(id, handle);
            handle.dispose();
        }
        return true;
    }

    /**
     * Removes the operation when it finished by itself.
     *
     * @param id     the operation id
     * @param handle the handle that was registered
     */
    void finished(String id, Disposable handle) {
        operations.remove(id, handle);
    }

    /**
     * Cancels the operation of this connection with that id, if there is one.
     *
     * @param id the operation id
     */
    void cancel(String id) {
        Disposable handle = operations.remove(id);
        if (handle != null) {
            handle.dispose();
        }
    }

    boolean closed() {
        return closed;
    }

    void close() {
        closed = true;
        cancelInitTimeout();
        for (String id : operations.keySet()) {
            cancel(id);
        }
        attributes = Collections.emptyMap();
    }

}
