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

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.results.RunResult;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;

class JmhSmokeTest {

    @Test
    void everyBenchmarkRuns() throws Exception {
        Options options = new OptionsBuilder()
                .include("io\\.micronaut\\.graphql\\.tools\\.benchmarks\\..*Benchmark\\..*")
                .param("fields", "100", "10000")
                .forks(0)
                .warmupIterations(0)
                .measurementIterations(1)
                .mode(Mode.SingleShotTime)
                .shouldFailOnError(true)
                .build();
        Collection<RunResult> results = new Runner(options).run();
        assertFalse(results.isEmpty(), "no benchmark ran");
    }
}
