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
package io.micronaut.graphql.tools.internal.execution.timeout;

import io.micronaut.core.annotation.Internal;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The outcome of one operation, decided once: the result of the execution or the timeout claims it first, and the
 * other does nothing.
 */
@Internal
public final class TimeoutClaim {

    private static final int RUNNING = 0;
    private static final int COMPLETED = 1;
    private static final int TIMED_OUT = 2;

    private final AtomicInteger outcome = new AtomicInteger(RUNNING);

    /**
     * @return true when the normal completion owns the outcome, which stays true for later calls of the same owner
     */
    public boolean completes() {
        return outcome.compareAndSet(RUNNING, COMPLETED) || outcome.get() == COMPLETED;
    }

    /**
     * @return true for the one call that claims the timeout, false when the operation completed first or the timeout was claimed already
     */
    public boolean timesOut() {
        return outcome.compareAndSet(RUNNING, TIMED_OUT);
    }
}
