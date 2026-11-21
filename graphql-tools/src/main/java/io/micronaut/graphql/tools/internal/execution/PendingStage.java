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
import io.micronaut.core.annotation.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * An async boundary of an execution that is still waiting for its source. The execution keeps its pending stages in a
 * list that runs through the stages themselves, so tracking one allocates nothing and cancelling the execution can reach
 * each of them.
 */
@Internal
abstract class PendingStage extends CompletableFuture<Object> {

    @Nullable
    PendingStage previous;
    @Nullable
    PendingStage next;
    boolean listed;

    /**
     * Cancels the source of this stage and completes the stage with an abort, because its execution was cancelled.
     */
    abstract void abort();
}
