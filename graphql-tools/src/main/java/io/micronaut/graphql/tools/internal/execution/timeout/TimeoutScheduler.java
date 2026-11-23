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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The scheduler of the operation timeout timers. It is the library's own and removes a cancelled timer from its queue at
 * once ({@code removeOnCancelPolicy}), which the shared {@code scheduled} executor of Micronaut does not: an operation
 * that finishes in time cancels its timer, and a long timeout would otherwise keep every such timer queued until its
 * deadline. It exists only when {@code micronaut.graphql.tools.execution.timeout} is set, runs on one daemon thread,
 * and that thread ends after a minute without a timer.
 */
@Internal
@Singleton
@Requires(property = GraphQLToolsConfiguration.PREFIX + ".execution.timeout")
public class TimeoutScheduler extends ScheduledThreadPoolExecutor {

    public TimeoutScheduler() {
        super(1, runnable -> {
            Thread thread = new Thread(runnable, "graphql-tools-timeout");
            thread.setDaemon(true);
            return thread;
        });
        setRemoveOnCancelPolicy(true);
        setKeepAliveTime(1, TimeUnit.MINUTES);
        allowCoreThreadTimeOut(true);
    }

    /**
     * Stops the scheduler with the application context.
     */
    @PreDestroy
    void stop() {
        shutdownNow();
    }
}
