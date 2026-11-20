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

import graphql.ExecutionResult;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The cost of the field decorators on one query that selects 1,050 fields below a root field (210 items of five fields): with
 * no decorator bean ({@code none}), with one that is offered every field and selects none ({@code unselective}) and with one
 * that wraps every field in a fetcher that delegates ({@code wrapAll}). The decision is made once per field at startup, so
 * {@code none} and {@code unselective} should not differ; {@code wrapAll} adds one delegating call per fetched field. Each
 * configuration checks its result against the one of {@code none}, and that the decorator did what its name says.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class FieldDecoratorBenchmark {

    static final int ITEMS = 210;

    @Param({"none", "unselective", "wrapAll"})
    public String decorator;

    private BenchmarkCases cases;
    private BenchmarkCases.Op query;

    @Setup
    public void prepare() throws Exception {
        BenchmarkCases.Decorators mode = switch (decorator) {
            case "none" -> BenchmarkCases.Decorators.NONE;
            case "unselective" -> BenchmarkCases.Decorators.UNSELECTIVE;
            default -> BenchmarkCases.Decorators.WRAP_ALL;
        };
        Resolvers.OFFERED.set(0);
        Resolvers.WRAPPED.set(0);
        cases = new BenchmarkCases(0, BenchmarkCases.Instrumented.NONE, mode);
        query = cases.wideQuery(ITEMS);
        verify(mode);
    }

    private void verify(BenchmarkCases.Decorators mode) throws Exception {
        int offered = Resolvers.OFFERED.get();
        int wrapped = Resolvers.WRAPPED.get();
        boolean ok = switch (mode) {
            case NONE -> offered == 0 && wrapped == 0;
            case UNSELECTIVE -> offered > 0 && wrapped == 0;
            case WRAP_ALL -> offered > 0 && wrapped == offered;
        };
        if (!ok) {
            throw new IllegalStateException(decorator + ": the decorator was offered " + offered + " fields and wrapped " + wrapped);
        }
        Map<String, Object> actual = ((ExecutionResult) query.run()).toSpecification();
        try (BenchmarkCases reference = new BenchmarkCases(0, BenchmarkCases.Instrumented.NONE, BenchmarkCases.Decorators.NONE)) {
            Map<String, Object> expected = ((ExecutionResult) reference.wideQuery(ITEMS).run()).toSpecification();
            if (!expected.equals(actual)) {
                throw new IllegalStateException(decorator + ": the result differs from the result without a decorator");
            }
        }
    }

    @TearDown
    public void tearDown() {
        cases.close();
    }

    @Benchmark
    public Object query1050Fields() throws Exception {
        return query.run();
    }
}
