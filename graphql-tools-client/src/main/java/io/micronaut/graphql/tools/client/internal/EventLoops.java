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
package io.micronaut.graphql.tools.client.internal;

import io.micronaut.core.annotation.Internal;

/**
 * Tells whether the current thread is an event loop thread of Netty. It compares class names, so it links to nothing of Netty
 * and works when the HTTP client is not the Netty one.
 */
@Internal
final class EventLoops {

    private static final String NETTY_THREAD = "io.netty.util.concurrent.FastThreadLocalThread";

    private EventLoops() {
    }

    static boolean isEventLoopThread() {
        for (Class<?> type = Thread.currentThread().getClass(); type != Thread.class && type != null; type = type.getSuperclass()) {
            if (NETTY_THREAD.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }
}
