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
import org.openjdk.jmh.annotations.Threads;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(4)
public class AbstractTypeBenchmark extends CaseState {

    private BenchmarkCases.Op union;
    private BenchmarkCases.Op iface;
    private BenchmarkCases.Op cachedType;
    private BenchmarkCases.Op control;

    @Setup
    public void prepare() {
        union = op(BenchmarkCases.UNION_RESOLUTION);
        iface = op(BenchmarkCases.INTERFACE_RESOLUTION);
        cachedType = op(BenchmarkCases.UNION_RESOLUTION_CACHED_TYPE);
        control = op(BenchmarkCases.UNION_RESOLUTION_CONTROL);
    }

    @Benchmark
    public Object unionResolution() throws Exception {
        return union.run();
    }

    @Benchmark
    public Object interfaceResolution() throws Exception {
        return iface.run();
    }

    @Benchmark
    public Object unionResolutionCachedType() throws Exception {
        return cachedType.run();
    }

    @Benchmark
    public Object unionResolutionControl() throws Exception {
        return control.run();
    }
}
