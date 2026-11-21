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
package example.tck;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import jakarta.inject.Singleton;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The fixtures of the TCK scenario {@code disconnect}: a field that completes after a delay and a child field of its
 * result, with a counter of how often each ran.
 */
public final class DisconnectScenario {

    static final String SCENARIO = "disconnect";

    private DisconnectScenario() {
    }

    @Singleton
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class Counters {

        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger childRuns = new AtomicInteger();
    }

    @GraphQLType
    public static class SlowParent {
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class Queries {

        private final Counters counters;

        public Queries(Counters counters) {
            this.counters = counters;
        }

        // tag::disconnect-slow-field[]
        public CompletionStage<SlowParent> slowParent() {
            counters.starts.incrementAndGet();
            return CompletableFuture.supplyAsync(SlowParent::new, CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS));
        }
        // end::disconnect-slow-field[]

        public int slowStarts() {
            return counters.starts.get();
        }

        public int childRuns() {
            return counters.childRuns.get();
        }
    }

    @Singleton
    @GraphQLTypeResolver(SlowParent.class)
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class SlowParentFields {

        private final Counters counters;

        public SlowParentFields(Counters counters) {
            this.counters = counters;
        }

        public String child(SlowParent parent) {
            counters.childRuns.incrementAndGet();
            return "ran";
        }
    }
}
