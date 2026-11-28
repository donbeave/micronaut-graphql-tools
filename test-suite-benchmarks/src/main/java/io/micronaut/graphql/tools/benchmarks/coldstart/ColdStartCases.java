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
package io.micronaut.graphql.tools.benchmarks.coldstart;

import io.micronaut.context.ApplicationContext;

import java.util.Map;

public final class ColdStartCases {

    private ColdStartCases() {
    }

    /**
     * Starts a context that has no server and resolves every generated client.
     *
     * @return the number of clients that the context resolved
     */
    public static int startAndResolve() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
                "micronaut.http.services." + ColdStartSuite.SERVICE + ".url", "http://localhost:1",
                "micronaut.application.name", "coldstart",
                "micronaut.security.enabled", false,
                "micronaut.metrics.enabled", false,
                "micronaut.otel.enabled", false,
                "graphql.enabled", false,
                "micronaut.graphql.tools.eager-init", false,
                "micronaut.graphql.tools.scalars.enabled", false))) {
            return ColdStartSuite.resolve(context);
        }
    }
}
