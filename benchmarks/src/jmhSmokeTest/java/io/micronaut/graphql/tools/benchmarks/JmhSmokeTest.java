package io.micronaut.graphql.tools.benchmarks;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.results.RunResult;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
