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

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class CaseState {

    private BenchmarkCases cases;

    // created on first use: JMH does not order the @Setup of a subclass after the one of its superclass
    BenchmarkCases.Op op(String name) {
        if (cases == null) {
            cases = new BenchmarkCases();
        }
        return cases.op(name);
    }

    @TearDown
    public void tearDown() {
        if (cases != null) {
            cases.close();
        }
    }
}
