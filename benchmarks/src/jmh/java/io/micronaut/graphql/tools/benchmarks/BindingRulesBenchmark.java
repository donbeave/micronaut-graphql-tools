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

import io.micronaut.graphql.tools.benchmarks.fixture.ClassElementFixture;
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
 * The per-class binding rules, in the form the compiler runs them, over a compiler {@code ClassElement} of {@code resolver} (two
 * root fields with scalar arguments), {@code input} (a resolver whose argument is an input class with properties) and
 * {@code constructor} (a resolver whose argument is an input class with constructor arguments). Each operation starts a model of its
 * own, reads the class through it and returns the findings, which are none for the fixture classes.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class BindingRulesBenchmark {

    @Param({"resolver", "input", "constructor"})
    public String subject;

    private ClassElementFixture fixture;
    private String className;

    @Setup
    public void prepare() {
        className = BindingRulesCases.className(subject);
        fixture = ClassElementFixture.open();
        if (!fixture.check(className).isEmpty()) {
            throw new IllegalStateException(subject + ": the fixture class has findings: " + fixture.check(className));
        }
    }

    @TearDown
    public void tearDown() {
        fixture.close();
    }

    @Benchmark
    public Object rules() {
        return fixture.check(className);
    }
}
