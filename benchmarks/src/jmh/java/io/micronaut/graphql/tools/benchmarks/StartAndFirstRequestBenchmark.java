package io.micronaut.graphql.tools.benchmarks;

import graphql.ExecutionResult;
import graphql.GraphQL;
import io.micronaut.context.ApplicationContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.concurrent.TimeUnit;

/**
 * Context start plus the first query, with the schema bound at the start or not
 * ({@code micronaut.graphql.tools.eager-init}): the time until the first response, which is what a request that arrives
 * right after the start waits for. One invocation is one fresh context.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class StartAndFirstRequestBenchmark extends AbstractEagerInitState {

    @Benchmark
    public ExecutionResult startAndQuery() {
        try (ApplicationContext context = start()) {
            checkBoundAtStart();
            ExecutionResult result = context.getBean(GraphQL.class).execute(QUERY);
            if (!result.getErrors().isEmpty() || result.getData() == null) {
                throw new IllegalStateException("The first query failed: " + result.getErrors());
            }
            return result;
        }
    }

}
