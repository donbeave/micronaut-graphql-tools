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

import io.micronaut.graphql.tools.benchmarks.coldstart.ColdStartCases;
import io.micronaut.graphql.tools.benchmarks.coldstart.ColdStartSuite;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ClientColdStartBenchmark {

    @Benchmark
    public int startAndResolveClients() {
        int clients = ColdStartCases.startAndResolve();
        if (clients != ColdStartSuite.CLIENTS) {
            throw new IllegalStateException("Resolved " + clients + " clients, expected " + ColdStartSuite.CLIENTS);
        }
        return clients;
    }
}
