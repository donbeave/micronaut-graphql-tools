package io.micronaut.graphql.tools.benchmarks;

import org.openjdk.jmh.runner.options.CommandLineOptions;

import java.util.Arrays;

/**
 * Main class of the benchmark jar. Without a benchmark selector it leaves out the start-up benchmarks, as the
 * {@code jmh} task does: they run only on a jar that generated the schema size they are given, one cold JVM per sample
 * (scripts/startup-report.py). Name them to run them: {@code java -jar <jar> BindingBenchmark -p fields=1600 ...}.
 */
public final class BenchmarkJarMain {

    static final String STARTUP = "BindingBenchmark|RetainedHeapBenchmark";

    private BenchmarkJarMain() {
    }

    public static void main(String[] args) throws Exception {
        CommandLineOptions parsed = new CommandLineOptions(args);
        boolean unselected = parsed.getIncludes().isEmpty() && parsed.getExcludes().isEmpty();
        if (unselected) {
            args = Arrays.copyOf(args, args.length + 2);
            args[args.length - 2] = "-e";
            args[args.length - 1] = STARTUP;
        }
        org.openjdk.jmh.Main.main(args);
    }
}
