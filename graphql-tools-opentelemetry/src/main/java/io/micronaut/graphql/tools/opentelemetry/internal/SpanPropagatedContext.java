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
package io.micronaut.graphql.tools.opentelemetry.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Makes an OpenTelemetry context, the one that holds a span, current on every thread that Micronaut's propagated context
 * reaches, so that a span started there (a {@code @NewSpan} method, an HTTP client call) is a child of it.
 *
 * @param context the OpenTelemetry context
 */
@Internal
record SpanPropagatedContext(Context context) implements ThreadPropagatedContextElement<Scope> {

    @Override
    public Scope updateThreadContext() {
        return context.makeCurrent();
    }

    @Override
    public void restoreThreadContext(Scope scope) {
        scope.close();
    }

}
