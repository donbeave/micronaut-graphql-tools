package io.micronaut.graphql.tools.benchmarks;

import io.micronaut.context.ApplicationContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ContextStartBenchmark extends AbstractEagerInitState {

    @Benchmark
    public ApplicationContext startContext() {
        ApplicationContext context = start();
        try {
            checkBoundAtStart();
            return context;
        } finally {
            context.close();
        }
    }

}
