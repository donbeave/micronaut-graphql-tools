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

import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.ApplicationContext;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Heap retained by a started context that binds a generated schema of {@code fields} fields, with the binding manifest
 * recorded or not. One invocation is one fresh context: the heap in use after a full collection, taken before the start
 * and again with the context open, and the difference is reported as the {@code retainedBytes} secondary result. The
 * registry the context is started with is in both figures, so the difference is what the context itself keeps: its
 * beans and the bound schema. The figure includes whatever the first start leaves in static state, which is why it is
 * taken in a fresh JVM (the default {@code -f 10 -wi 0 -i 1} of the start-up benchmarks).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class RetainedHeapBenchmark {

    @Param({"100", "1600", "3000", "10000", "20000"})
    public int fields;

    @Param({"false", "true"})
    public boolean record;

    private TypeDefinitionRegistry registry;

    /** The secondary result: the bytes the context retains. */
    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class Retained {
        public long retainedBytes;
    }

    @Setup
    public void parse() {
        registry = LargeSchema.registry(fields);
    }

    @Benchmark
    public void retainedHeap(Retained retained) {
        long before = usedHeapAfterGc();
        try (ApplicationContext context = start()) {
            retained.retainedBytes = usedHeapAfterGc() - before;
            LargeSchema.checkBound(context);
            if (LargeSchema.manifestAvailable(context) != record) {
                throw new IllegalStateException("The manifest is " + (record ? "not " : "") + "recorded, but record is "
                        + record);
            }
        }
    }

    // System.gc() is a request. Repeat it until the figure stops falling, so that the figure is what is still reachable.
    private static long usedHeapAfterGc() {
        Runtime runtime = Runtime.getRuntime();
        long used = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            System.gc();
            long next = runtime.totalMemory() - runtime.freeMemory();
            if (next >= used) {
                break;
            }
            used = next;
        }
        return used;
    }

    private ApplicationContext start() {
        return ApplicationContext.builder()
                .properties(Map.of(
                        "graphql.graphql-ws.enabled", false,
                        "graphql.graphiql.enabled", false,
                        "benchmarks.large", true,
                        LargeSchema.FIELDS, fields,
                        "micronaut.graphql.tools.manifest.record", record))
                .singletons(registry)
                .start();
    }
}
