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

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Reads through the introspection of an interface. {@code one} reads one interface of the class, {@code two} alternates
 * two interfaces of the same class. On JDK 21 and earlier, checking one class against two interfaces alternately
 * contends on the secondary-super cache of the class (fixed in JDK 23), so {@code two} may cost more per read there.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class InterfaceReadBenchmark {

    @Param({"one", "two"})
    public String interfaces;

    private BeanProperty<InterfaceReads.Named, String> name;
    private BeanProperty<InterfaceReads.Labelled, String> label;
    private InterfaceReads.Item item;
    private boolean alternate;

    @Setup
    public void prepare() {
        name = BeanIntrospection.getIntrospection(InterfaceReads.Named.class).getRequiredProperty("name", String.class);
        label = BeanIntrospection.getIntrospection(InterfaceReads.Labelled.class).getRequiredProperty("label", String.class);
        item = new InterfaceReads.Item("n", "l");
        alternate = "two".equals(interfaces);
        String expected = alternate ? "nl" : "nn";
        String actual = read() + read();
        if (!expected.equals(actual)) {
            throw new IllegalStateException(interfaces + ": read " + actual + ", expected " + expected);
        }
    }

    private int turn;

    private String read() {
        return alternate && (turn++ & 1) == 1 ? label.get(item) : name.get(item);
    }

    @Benchmark
    public void read2(Blackhole blackhole) {
        blackhole.consume(read());
        blackhole.consume(read());
    }
}
