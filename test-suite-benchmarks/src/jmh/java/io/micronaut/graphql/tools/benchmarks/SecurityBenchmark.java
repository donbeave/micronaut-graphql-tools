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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;

/**
 * An unsecured field and a secured field that passes, with the security module installed, beside the same unsecured field
 * without it (#595).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SecurityBenchmark {

    private BenchmarkCases without;
    private BenchmarkCases with;
    private BenchmarkCases.Op unsecuredWithoutModule;
    private BenchmarkCases.Op unsecuredWithModule;
    private BenchmarkCases.Op securedPasses;

    @Setup
    public void prepare() {
        without = new BenchmarkCases();
        with = new BenchmarkCases(0, BenchmarkCases.Instrumented.NONE, BenchmarkCases.Decorators.UNSELECTIVE, true);
        unsecuredWithoutModule = without.op(BenchmarkCases.ROOT_METHOD);
        unsecuredWithModule = with.op(BenchmarkCases.SECURITY_UNSECURED_FIELD);
        securedPasses = with.op(BenchmarkCases.SECURITY_SECURED_FIELD_PASSES);
    }

    @TearDown
    public void tearDown() {
        with.close();
        without.close();
    }

    @Benchmark
    public Object unsecuredFieldWithoutModule() throws Exception {
        return unsecuredWithoutModule.run();
    }

    @Benchmark
    public Object unsecuredFieldWithModule() throws Exception {
        return unsecuredWithModule.run();
    }

    @Benchmark
    public Object securedFieldPasses() throws Exception {
        return securedPasses.run();
    }
}
