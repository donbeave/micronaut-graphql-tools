package io.micronaut.graphql.tools.benchmarks;

import io.micronaut.context.ApplicationContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.concurrent.TimeUnit;

/**
 * Time to start a context for the large schema (and close it), with the schema bound at the start or not
 * ({@code micronaut.graphql.tools.eager-init}). One invocation is one fresh context. The counterpart is
 * {@link FirstRequestBenchmark}: the binding work moves between the two.
 */
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
