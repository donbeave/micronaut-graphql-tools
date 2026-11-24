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
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The fixtures of the TCK scenario {@code http-conformance}: a query that echoes its argument, a mutation that echoes it and
 * counts its runs, and the count, so that a check can prove that a refused request ran nothing.
 */
public final class HttpConformanceScenario {

    private HttpConformanceScenario() {
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "http-conformance")
    public static class Resolvers {

        private final AtomicInteger count = new AtomicInteger();

        public String confEcho(String value) {
            return value;
        }

        public String confMutate(String value) {
            count.incrementAndGet();
            return value;
        }

        public int confMutationCount() {
            return count.get();
        }
    }
}
