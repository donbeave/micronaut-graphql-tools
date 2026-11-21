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
package io.micronaut.graphql.tools.buildtools;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * The compile-time budget (#418). It reads what the gradle-profiler scenarios of the samples measured, compares the median of each
 * budgeted scenario with its budget, and writes a report: the build time of every scenario with the checks on and off, and the
 * compile time of every module and task. The class has a {@code main} that the Gradle task {@code compileTimeBudget} runs.
 *
 * <p>Layout of the results, one directory per sample under the output directory: {@code benchmark.csv} (gradle-profiler) and
 * {@code compile-tasks.txt} (one line per compile task that ran: {@code <scenario> <project> <task> <milliseconds>}).
 * A budget key is {@code <sample>.clean} or {@code <sample>.incremental}; the incremental budget holds for the body edit and for
 * the schema edit alike. Scenario names are {@code <sample>-<clean|body|schema>-<on|off>}.
 */
public final class CompileTimeBudget {

    private static final String MEASURED = "measured build #";

    private CompileTimeBudget() {
    }

    /** What a check found: the report and the budgets that were exceeded or could not be judged. */
    record Outcome(String report, List<String> violations) {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            throw new IllegalArgumentException("usage: <output-dir> <budget-file> <report-file> <iterations>");
        }
        Properties budget = new Properties();
        budget.load(java.nio.file.Files.newBufferedReader(Path.of(args[1])));
        Outcome outcome = check(Path.of(args[0]), budget, Integer.parseInt(args[3]));
        Path report = Path.of(args[2]);
        java.nio.file.Files.createDirectories(report.getParent());
        java.nio.file.Files.writeString(report, outcome.report());
        System.out.println(outcome.report());
        if (!outcome.violations().isEmpty()) {
            System.err.println("Compile-time budget exceeded:");
            outcome.violations().forEach(v -> System.err.println("  " + v));
            System.exit(1);
        }
    }

    static Outcome check(Path output, Properties budget, int iterations) throws IOException {
        Map<String, Map<String, Long>> medians = new TreeMap<>();
        Map<String, Map<String, Long>> modules = new TreeMap<>();
        for (String sample : samples(budget)) {
            Path dir = output.resolve(sample);
            Path csv = dir.resolve("benchmark.csv");
            medians.put(sample, java.nio.file.Files.exists(csv)
                    ? medians(java.nio.file.Files.readAllLines(csv)) : Map.of());
            Path tasks = dir.resolve("compile-tasks.txt");
            modules.put(sample, java.nio.file.Files.exists(tasks)
                    ? taskMedians(java.nio.file.Files.readAllLines(tasks), iterations) : Map.of());
        }
        List<String> violations = new ArrayList<>();
        StringBuilder report = new StringBuilder();
        report.append("### Compile time of the checks, build time by gradle-profiler (median of the measured builds, milliseconds)\n\n");
        report.append("| sample | scenario | checks on | checks off | cost of the checks | budget |\n| --- | --- | ---: | ---: | ---: | ---: |\n");
        for (String sample : medians.keySet()) {
            for (String edit : List.of("clean", "body", "schema")) {
                String kind = edit.equals("clean") ? "clean" : "incremental";
                Long on = medians.get(sample).get(sample + "-" + edit + "-on");
                Long off = medians.get(sample).get(sample + "-" + edit + "-off");
                String limit = budget.getProperty(sample + "." + kind);
                report.append("| ").append(sample).append(" | ").append(edit).append(" | ").append(cell(on)).append(" | ")
                        .append(cell(off)).append(" | ").append(on == null || off == null ? "-" : String.valueOf(on - off))
                        .append(" | ").append(limit == null ? "-" : limit).append(" |\n");
                if (limit != null) {
                    if (on == null) {
                        violations.add(sample + "-" + edit + "-on: not measured");
                    } else if (on > Long.parseLong(limit.trim())) {
                        violations.add(sample + "-" + edit + "-on: " + on + " ms exceeds the " + kind + " budget of " + limit + " ms");
                    }
                }
            }
        }
        report.append("\n### Compile task time per module (median of the last ").append(iterations)
                .append(" runs, milliseconds)\n\n| sample | scenario | module | task | checks on | checks off | cost of the checks |\n")
                .append("| --- | --- | --- | --- | ---: | ---: | ---: |\n");
        modules.forEach((sample, byKey) -> byKey.keySet().stream().filter(k -> k.startsWith(sample + "-") && k.contains("-on|"))
                .forEach(onKey -> {
                    String offKey = onKey.replace("-on|", "-off|");
                    String[] parts = onKey.split("\\|");
                    String scenario = parts[0].substring(sample.length() + 1, parts[0].length() - "-on".length());
                    Long on = byKey.get(onKey);
                    Long off = byKey.get(offKey);
                    report.append("| ").append(sample).append(" | ").append(scenario).append(" | ").append(parts[1]).append(" | ")
                            .append(parts[2]).append(" | ").append(on).append(" | ").append(cell(off)).append(" | ")
                            .append(off == null ? "-" : String.valueOf(on - off)).append(" |\n");
                }));
        return new Outcome(report.toString(), violations);
    }

    private static String cell(Long value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private static List<String> samples(Properties budget) {
        return budget.stringPropertyNames().stream().map(k -> k.substring(0, k.lastIndexOf('.'))).distinct().sorted().toList();
    }

    /** The median of the measured builds of each scenario column of a gradle-profiler benchmark.csv. */
    static Map<String, Long> medians(List<String> csv) {
        List<String> names = List.of(csv.get(0).split(",")).subList(1, csv.get(0).split(",").length);
        Map<String, List<Long>> values = new LinkedHashMap<>();
        names.forEach(name -> values.put(name, new ArrayList<>()));
        for (String line : csv) {
            if (!line.startsWith(MEASURED)) {
                continue;
            }
            String[] cells = line.split(",");
            for (int i = 0; i < names.size(); i++) {
                values.get(names.get(i)).add(Math.round(Double.parseDouble(cells[i + 1])));
            }
        }
        Map<String, Long> result = new LinkedHashMap<>();
        values.forEach((name, list) -> {
            if (!list.isEmpty()) {
                result.put(name, median(list));
            }
        });
        return result;
    }

    /** Key {@code <scenario>|<project>|<task>}: the median of the last {@code iterations} runs of the task in the scenario. */
    static Map<String, Long> taskMedians(List<String> lines, int iterations) {
        Map<String, List<Long>> values = new LinkedHashMap<>();
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 4) {
                values.computeIfAbsent(parts[0] + "|" + parts[1] + "|" + parts[2], k -> new ArrayList<>()).add(Long.parseLong(parts[3]));
            }
        }
        Map<String, Long> result = new LinkedHashMap<>();
        values.forEach((key, list) -> result.put(key, median(list.subList(Math.max(0, list.size() - iterations), list.size()))));
        return result;
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : Math.round((sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0);
    }

    static Properties parse(String text) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        return properties;
    }
}
