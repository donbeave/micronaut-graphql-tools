/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graphql.tools.benchmarks;

import org.openjdk.jmh.runner.options.CommandLineOptions;

import java.util.Arrays;

/**
 * Main class of the benchmark jar. Unless a benchmark is included by name it leaves out the start-up benchmarks, as the
 * {@code jmh} task does: they run only on a jar that generated the schema size they are given, one cold JVM per sample
 * (scripts/startup-report.py). Name them to run them: {@code java -jar <jar> BindingBenchmark -p fields=1600 ...}.
 */
public final class BenchmarkJarMain {

    static final String STARTUP = "BindingBenchmark|RetainedHeapBenchmark|ClientColdStartBenchmark";

    private BenchmarkJarMain() {
    }

    public static void main(String[] args) throws Exception {
        CommandLineOptions parsed = new CommandLineOptions(args);
        if (parsed.getIncludes().isEmpty()) {
            args = Arrays.copyOf(args, args.length + 2);
            args[args.length - 2] = "-e";
            args[args.length - 1] = STARTUP;
        }
        org.openjdk.jmh.Main.main(args);
    }
}
