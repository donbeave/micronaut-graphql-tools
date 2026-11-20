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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;

/**
 * Tells an execution that the client of its HTTP request went away. The server does not cancel the response of a request
 * that is still being answered when the connection closes, so a transport-specific bean does it: the one implementation is
 * a bean that exists only when the Netty server classes are on the classpath, so an application on another server, and its
 * native image, never link one.
 */
@Internal
public interface DisconnectWatcher {

    /**
     * Starts to watch the connection of a request.
     *
     * @param request the request of the execution
     * @param onClose called, on any thread, when the connection closes
     * @return the call that stops the watch; the execution makes it when it has its result
     */
    Runnable watch(HttpRequest<?> request, Runnable onClose);

}
