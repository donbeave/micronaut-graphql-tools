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
package io.micronaut.graphql.tools.observability.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.observability.GraphQLObservability;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns the operation name of a request into the value of a tag. A client picks the name, so an unbounded set of names would make an
 * unbounded set of timers: the first {@code max} distinct names keep their own value and every later name is
 * {@link GraphQLObservability#OTHER_OPERATION}. The cap is exact, whatever the number of threads.
 */
@Internal
final class OperationNames {

    private final int max;
    private final ConcurrentHashMap<String, Boolean> known = new ConcurrentHashMap<>();

    OperationNames(int max) {
        this.max = max;
    }

    String tag(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return GraphQLObservability.ANONYMOUS_OPERATION;
        }
        if (known.containsKey(name)) {
            return name;
        }
        if (known.size() >= max) {
            return GraphQLObservability.OTHER_OPERATION;
        }
        synchronized (known) {
            if (known.containsKey(name)) {
                return name;
            }
            if (known.size() >= max) {
                return GraphQLObservability.OTHER_OPERATION;
            }
            known.put(name, Boolean.TRUE);
            return name;
        }
    }

}
