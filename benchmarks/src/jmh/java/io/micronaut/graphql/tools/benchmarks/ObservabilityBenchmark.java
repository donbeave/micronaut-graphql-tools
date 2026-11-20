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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;

/**
 * The cost of the observability module on a request of {@code rootFields} root fields: with no instrumentation ({@code none}), with
 * one that implements no hook ({@code empty}: what graphql-java charges for any instrumentation) and with the module's beans on
 * and no field selected for a span ({@code observed}). {@code observed} and {@code empty} should differ by a constant (the
 * timer, the request span), not by a cost that grows with {@code fields}. In {@code selected} every root field is selected for a
 * span by the field decorator: the difference to {@code observed}, divided by {@code rootFields}, is the cost of one span.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ObservabilityBenchmark {

    @Param({"none", "empty", "observed", "selected"})
    public String mode;

    @Param({"10", "1000"})
    public int rootFields;

    private BenchmarkCases cases;
    private BenchmarkCases.Op op;

    @Setup
    public void prepare() {
        cases = new BenchmarkCases(0, switch (mode) {
            case "none" -> BenchmarkCases.Instrumented.NONE;
            case "empty" -> BenchmarkCases.Instrumented.EMPTY;
            case "selected" -> BenchmarkCases.Instrumented.OBSERVABILITY_SELECTED;
            default -> BenchmarkCases.Instrumented.OBSERVABILITY;
        });
        op = cases.op(BenchmarkCases.invokeOp(rootFields));
    }

    @TearDown
    public void tearDown() {
        cases.close();
    }

    @Benchmark
    public Object request() throws Exception {
        return op.run();
    }
}
