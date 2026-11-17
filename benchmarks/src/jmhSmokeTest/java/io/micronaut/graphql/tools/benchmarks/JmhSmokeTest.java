package io.micronaut.graphql.tools.benchmarks;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.results.RunResult;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Runs every benchmark of this module once, in this JVM (no fork, one iteration, one invocation), so that they
 * compile, start and complete on every {@code check}. The figures mean nothing: see the contributor guide for the
 * measuring runs.
 */
class JmhSmokeTest {

    @Test
    void everyBenchmarkRuns() throws Exception {
        Options options = new OptionsBuilder()
                .include("io\\.micronaut\\.graphql\\.tools\\.benchmarks\\..*Benchmark\\..*")
                .forks(0)
                .warmupIterations(0)
                .measurementIterations(1)
                .mode(Mode.SingleShotTime)
                .shouldFailOnError(true)
                .build();
        Collection<RunResult> results = new Runner(options).run();
        assertFalse(results.isEmpty(), "no benchmark ran");
    }
}
