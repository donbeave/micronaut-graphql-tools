package io.micronaut.graphql.tools.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class QueryBenchmark extends CaseState {

    private BenchmarkCases.Op query1k;
    private BenchmarkCases.Op query10k;

    @Setup
    public void prepare() {
        query1k = op(BenchmarkCases.QUERY_1K);
        query10k = op(BenchmarkCases.QUERY_10K);
    }

    @Benchmark
    public Object query1kObjects() throws Exception {
        return query1k.run();
    }

    @Benchmark
    public Object query10kObjects() throws Exception {
        return query10k.run();
    }
}
