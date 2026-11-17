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

    @Setup
    public void prepare() {
        union = op(BenchmarkCases.UNION_RESOLUTION);
        iface = op(BenchmarkCases.INTERFACE_RESOLUTION);
    }

    @Benchmark
    public Object unionResolution() throws Exception {
        return union.run();
    }

    @Benchmark
    public Object interfaceResolution() throws Exception {
        return iface.run();
    }
}
