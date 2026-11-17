package io.micronaut.graphql.tools.benchmarks;

import graphql.ExecutionResult;
import graphql.GraphQL;
import io.micronaut.context.ApplicationContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;

/**
 * Time of the first query after the context started (the start is outside the measurement), with the schema bound at
 * the start or not ({@code micronaut.graphql.tools.eager-init}): the query looks up the {@code GraphQL} bean and
 * executes. One invocation is one fresh context, so the figure includes the first use of the classes the request
 * needs, with the binding added when it was not done at the start.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class FirstRequestBenchmark extends AbstractEagerInitState {

    private ApplicationContext context;

    @Setup(Level.Invocation)
    public void startContext() {
        context = start();
        checkBoundAtStart();
    }

    @TearDown(Level.Invocation)
    public void closeContext() {
        try {
            if (bindings.get() != 1) {
                throw new IllegalStateException(bindings.get() + " bindings after the first query, expected 1");
            }
        } finally {
            context.close();
        }
    }

    @Benchmark
    public ExecutionResult firstQuery() {
        ExecutionResult result = context.getBean(GraphQL.class).execute(QUERY);
        if (!result.getErrors().isEmpty() || result.getData() == null) {
            throw new IllegalStateException("The first query failed: " + result.getErrors());
        }
        return result;
    }

}
