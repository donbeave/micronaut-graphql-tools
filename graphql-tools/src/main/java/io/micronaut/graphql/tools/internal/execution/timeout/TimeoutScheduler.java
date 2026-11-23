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
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The scheduler of the operation timeout timers. It is the library's own and removes a cancelled timer from its queue at
 * once ({@code removeOnCancelPolicy}), which the shared {@code scheduled} executor of Micronaut does not: an operation
 * that finishes in time cancels its timer, and a long timeout would otherwise keep every such timer queued until its
 * deadline. The timer thread completes the result of a timed-out operation and hands the cancellation, which runs
 * application callbacks that may block, to an executor made for blocking work, so one blocked callback delays no other
 * timeout. The bean is always present and starts no thread until
 * the first timer is scheduled, which happens only when an execution timeout is configured; its threads are daemon
 * threads that end after a minute without work.
 */
@Internal
@Singleton
public class TimeoutScheduler extends ScheduledThreadPoolExecutor {

    private final ExecutorService blocking = Executors.newCachedThreadPool(runnable -> daemon(runnable, "graphql-tools-timeout-worker"));

    public TimeoutScheduler() {
        super(1, runnable -> daemon(runnable, "graphql-tools-timeout"));
        setRemoveOnCancelPolicy(true);
        setKeepAliveTime(1, TimeUnit.MINUTES);
        allowCoreThreadTimeOut(true);
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    /**
     * @param action the timeout of one operation, which runs on the timer thread: it completes the result and hands
     *               everything that may block to {@link #blocking}
     * @param nanos  the timeout
     * @return the timer, which removes itself from the queue when cancelled
     */
    public ScheduledFuture<?> scheduleTimeout(Runnable action, long nanos) {
        return schedule(action, nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * @return an executor for work that may block, such as a cancellation callback of the application: it starts a
     *         thread when none is idle, so one blocked callback holds back no other
     */
    public ExecutorService blocking() {
        return blocking;
    }

    /**
     * Stops the scheduler with the application context.
     */
    @PreDestroy
    void stop() {
        blocking.shutdownNow();
        shutdownNow();
    }
}
