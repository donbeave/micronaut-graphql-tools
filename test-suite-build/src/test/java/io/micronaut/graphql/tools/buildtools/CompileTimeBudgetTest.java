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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The budget check and the scenario definitions of the compile-time profile (#418). */
class CompileTimeBudgetTest {

    private static final List<String> SAMPLES = List.of("java-gradle", "kotlin-ksp", "kotlin-kapt", "groovy-gradle", "multi-module-gradle");
    private static final List<String> CLIENT_SAMPLES = List.of("java-client-gradle", "kotlin-ksp-client", "groovy-client-gradle");
    private static final Path PROFILER = Path.of(System.getProperty("sample.dir")).resolveSibling("profiler");

    @Test
    void scenariosCoverEverySampleEditAndMode() throws IOException {
        String text = java.nio.file.Files.readString(PROFILER.resolve("compile-time.scenarios"));
        for (String sample : SAMPLES) {
            assertEquals(List.of("clean", "body", "schema"), CompileTimeBudget.edits(sample), sample);
        }
        for (String sample : CLIENT_SAMPLES) {
            assertEquals(List.of("clean", "body", "document"), CompileTimeBudget.edits(sample), sample);
        }
        for (String sample : java.util.stream.Stream.concat(SAMPLES.stream(), CLIENT_SAMPLES.stream()).toList()) {
            for (String edit : CompileTimeBudget.edits(sample)) {
                for (String mode : List.of("on", "off")) {
                    String name = sample + "-" + edit + "-" + mode;
                    Matcher scenario = Pattern.compile("(?m)^" + Pattern.quote(name) + " \\{\n((?:  .*\n)+)\\}").matcher(text);
                    assertTrue(scenario.find(), "no scenario " + name);
                    String body = scenario.group(1);
                    assertTrue(body.contains("\"-PprofilerScenario=" + name + "\""), name + " does not carry its own name");
                    assertTrue(body.contains("${" + mode + "}"), name + " does not set the checks " + mode);
                    assertEquals(edit.equals("clean"), body.contains("cleanup-tasks = [\"clean\"]"), name);
                    assertEquals(edit.equals("body"), body.contains("editBody"), name);
                    assertEquals(edit.equals("schema"), body.contains("editSchema"), name);
                    assertEquals(edit.equals("document"), body.contains("editDocument"), name);
                }
            }
        }
    }

    @Test
    void budgetNamesACleanAndAnIncrementalLimitForEachSample() throws IOException {
        var budget = CompileTimeBudget.parse(java.nio.file.Files.readString(PROFILER.resolve("compile-time-budget.properties")));
        for (String sample : java.util.stream.Stream.concat(SAMPLES.stream(), CLIENT_SAMPLES.stream()).toList()) {
            for (String kind : List.of("clean", "incremental")) {
                assertTrue(Long.parseLong(budget.getProperty(sample + "." + kind, "").trim()) > 0, "no " + kind + " budget for " + sample);
            }
        }
    }

    @Test
    void medianOfTheMeasuredBuilds() {
        Map<String, Long> medians = CompileTimeBudget.medians(List.of(
                "scenario,a-clean-on,a-clean-off",
                "version,Gradle 9,Gradle 9",
                "warm-up build #1,9000.0,9000.0",
                "measured build #1,100.4,50.0",
                "measured build #2,300.0,70.0",
                "measured build #3,200.0,60.0",
                "measured build #4,400.0,80.0"));
        assertEquals(250L, medians.get("a-clean-on"));
        assertEquals(65L, medians.get("a-clean-off"));
    }

    @Test
    void failsWhenAMeasuredTimeExceedsItsBudget(@org.junit.jupiter.api.io.TempDir Path output) throws IOException {
        Path sample = java.nio.file.Files.createDirectories(output.resolve("a"));
        java.nio.file.Files.write(sample.resolve("benchmark.csv"), List.of(
                "scenario,a-clean-on,a-clean-off,a-body-on,a-body-off,a-schema-on,a-schema-off",
                "measured build #1,900,800,400,300,1500,300",
                "measured build #2,1000,850,500,300,1700,300",
                "measured build #3,1100,900,600,300,1600,300"));
        java.nio.file.Files.write(sample.resolve("compile-tasks.txt"), List.of(
                "a-clean-on :books compileJava 700", "a-clean-off :books compileJava 300"));
        var budget = CompileTimeBudget.parse("a.clean=1000\na.incremental=1000\n");
        CompileTimeBudget.Outcome outcome = CompileTimeBudget.check(output, budget, 3);
        assertEquals(List.of("a-schema-on: 1600 ms exceeds the incremental budget of 1000 ms"), outcome.violations());
        assertTrue(outcome.report().contains("| a | clean | 1000 | 850 | 150 | 1000 |"), outcome.report());
        assertTrue(outcome.report().contains("| a | clean | :books | compileJava | 700 | 300 | 400 |"), outcome.report());
    }

    @Test
    void failsWhenACleanBuildWithTheChecksIsMoreThan125TimesTheBuildWithout(@org.junit.jupiter.api.io.TempDir Path output)
            throws IOException {
        Path sample = java.nio.file.Files.createDirectories(output.resolve("a"));
        java.nio.file.Files.write(sample.resolve("benchmark.csv"), List.of(
                "scenario,a-clean-on,a-clean-off,a-body-on,a-body-off,a-schema-on,a-schema-off",
                "measured build #1,1300,1000,400,300,400,300"));
        var budget = CompileTimeBudget.parse("a.clean=5000\na.incremental=5000\n");
        assertEquals(List.of("a-clean-on: 1300 ms is more than 1.25 times the 1000 ms of the checks-off build"),
                CompileTimeBudget.check(output, budget, 3).violations());
    }

    @Test
    void theClientSamplesEditTheirDocumentAndTheOthersTheirSchema() {
        assertEquals(List.of("clean", "body", "document"), CompileTimeBudget.edits("java-client-gradle"));
        assertEquals(List.of("clean", "body", "schema"), CompileTimeBudget.edits("java-gradle"));
    }

    @Test
    void failsWhenABudgetedScenarioWasNotMeasured(@org.junit.jupiter.api.io.TempDir Path output) throws IOException {
        var budget = CompileTimeBudget.parse("a.clean=1000\na.incremental=1000\n");
        assertEquals(3, CompileTimeBudget.check(output, budget, 3).violations().size());
    }
}
