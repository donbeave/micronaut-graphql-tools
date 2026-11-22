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

import graphql.ExecutionInput;
import io.micronaut.configuration.graphql.GraphQLExecutionInputCustomizer;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.tck.TckContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The fixtures of the TCK scenario {@code customizer}: an execution-input customizer that counts its calls, subscriptions
 * and cancellations and records the Reactor context entry it saw, for an operation whose variables hold {@code track: true};
 * with {@code hold: true} its publisher never emits.
 */
public final class CustomizerScenario {

    static final String SCENARIO = "customizer";

    private CustomizerScenario() {
    }

    @Singleton
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class Counters {

        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger subscribes = new AtomicInteger();
        final AtomicInteger cancels = new AtomicInteger();
        final AtomicInteger trackedRuns = new AtomicInteger();
        final AtomicReference<String> seen = new AtomicReference<>("none");
        final AtomicReference<String> context = new AtomicReference<>("none");
    }

    @Singleton
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class Customizer implements GraphQLExecutionInputCustomizer {

        private final Counters counters;

        public Customizer(Counters counters) {
            this.counters = counters;
        }

        @Override
        public Publisher<ExecutionInput> customize(ExecutionInput input, HttpRequest request, MutableHttpResponse<String> response) {
            if (!Boolean.TRUE.equals(input.getVariables().get("track"))) {
                return Mono.just(input);
            }
            counters.calls.incrementAndGet();
            Mono<ExecutionInput> result = Boolean.TRUE.equals(input.getVariables().get("hold"))
                    ? Mono.never()
                    : Mono.deferContextual(context -> {
                        counters.seen.set(context.getOrDefault("tck-key", "none"));
                        return Mono.just(input);
                    });
            return result.doOnSubscribe(subscription -> counters.subscribes.incrementAndGet())
                    .doOnCancel(counters.cancels::incrementAndGet);
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class Queries {

        private final Counters counters;

        public Queries(Counters counters) {
            this.counters = counters;
        }

        public int customizerCalls() {
            return counters.calls.get();
        }

        public int customizerSubscribes() {
            return counters.subscribes.get();
        }

        public int customizerCancels() {
            return counters.cancels.get();
        }

        public String customizerSeen() {
            return counters.seen.get();
        }

        public String tracked() {
            counters.trackedRuns.incrementAndGet();
            counters.context.set(PropagatedContext.getOrEmpty().find(TckContextElement.class).map(TckContextElement::id).orElse("none"));
            return "ran";
        }

        public String trackedContext() {
            return counters.context.get();
        }

        public int trackedRuns() {
            return counters.trackedRuns.get();
        }
    }
}
