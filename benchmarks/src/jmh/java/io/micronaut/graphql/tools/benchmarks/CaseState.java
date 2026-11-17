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
