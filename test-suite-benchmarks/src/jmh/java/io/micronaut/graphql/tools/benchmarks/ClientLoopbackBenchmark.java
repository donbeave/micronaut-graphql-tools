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

import io.micronaut.graphql.tools.benchmarks.loopback.LoopbackCases;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;

/**
 * A blocking call of an embedded server that answers a fixed body of 1 KB, through the declarative client, the low-level client and
 * a plain declarative HTTP client (#686). The plain client is the reference: the others are judged against it.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ClientLoopbackBenchmark {

    private LoopbackCases cases;
    private BenchmarkCases.Op declarative;
    private BenchmarkCases.Op lowLevel;
    private BenchmarkCases.Op raw;

    @Setup
    public void prepare() {
        cases = new LoopbackCases();
        declarative = cases.op(LoopbackCases.CLIENT_DECLARATIVE_LOOPBACK);
        lowLevel = cases.op(LoopbackCases.CLIENT_LOW_LEVEL_LOOPBACK);
        raw = cases.op(LoopbackCases.CLIENT_RAW_LOOPBACK);
    }

    @TearDown
    public void tearDown() {
        cases.close();
    }

    @Benchmark
    public Object clientDeclarativeLoopback() throws Exception {
        return declarative.run();
    }

    @Benchmark
    public Object clientLowLevelLoopback() throws Exception {
        return lowLevel.run();
    }

    @Benchmark
    public Object clientRawLoopback() throws Exception {
        return raw.run();
    }
}
