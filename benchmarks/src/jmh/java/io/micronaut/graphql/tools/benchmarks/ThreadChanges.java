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
package io.micronaut.graphql.tools.benchmarks;

import reactor.core.scheduler.NonBlocking;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts what the execution threads did during one operation, so that a benchmark asserts the number of hops instead of
 * assuming it. It is attached to a verification run only; the measured run has none, so it adds nothing to a measurement.
 */
final class ThreadChanges {

    private final AtomicInteger handoffs = new AtomicInteger();
    private final AtomicInteger changes = new AtomicInteger();
    private final AtomicInteger fetches = new AtomicInteger();
    private final AtomicInteger offCallerFetches = new AtomicInteger();
    private final AtomicInteger nonBlockingFetches = new AtomicInteger();
    private volatile Thread caller;

    /** Starts one operation: the calling thread is the one that does not count as a change. */
    void begin() {
        handoffs.set(0);
        changes.set(0);
        fetches.set(0);
        offCallerFetches.set(0);
        nonBlockingFetches.set(0);
        caller = Thread.currentThread();
    }

    /** A task handed to an executor: a change when it starts on another thread than the one that submitted it. */
    void task(Thread submitter) {
        handoffs.incrementAndGet();
        if (Thread.currentThread() != submitter) {
            changes.incrementAndGet();
        }
    }

    /** A data fetcher ran. */
    void fetch() {
        fetches.incrementAndGet();
        Thread current = Thread.currentThread();
        if (current != caller) {
            offCallerFetches.incrementAndGet();
        }
        if (current instanceof NonBlocking) {
            nonBlockingFetches.incrementAndGet();
        }
    }

    Executor counting(Executor delegate) {
        return task -> {
            Thread submitter = Thread.currentThread();
            delegate.execute(() -> {
                task(submitter);
                task.run();
            });
        };
    }

    /** Fails when a count differs; a negative expected value is not checked. */
    void assertCounts(String what, int handoffs, int changes, int fetches, int offCaller, int nonBlocking) {
        int[] expected = {handoffs, changes, fetches, offCaller, nonBlocking};
        int[] actual = {this.handoffs.get(), this.changes.get(), this.fetches.get(), offCallerFetches.get(),
                nonBlockingFetches.get()};
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] >= 0 && expected[i] != actual[i]) {
                throw new IllegalStateException(what + ": expected handoffs, changes, fetches, offCaller, nonBlocking = "
                        + java.util.Arrays.toString(expected) + " but counted " + java.util.Arrays.toString(actual));
            }
        }
    }
}
