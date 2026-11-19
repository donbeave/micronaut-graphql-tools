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

import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the incrementality of the compile-time checks rests on, shown by a differential build (#13). The same project is built
 * twice, once with the processor and once as a control without it, and both get the same edits. The structural evidence is the
 * set of class files that an incremental build rewrote (a class file whose modification time, size or presence changed): the
 * set of the build with the processor must equal the set of the control for every kind of edit, so the visitor makes the
 * compiler redo no work that it would not redo anyway. The specs assert no wall-clock time: the times are recorded, and the
 * numbers depend on the machine.
 *
 * <p>Every edit is also built from scratch, in a copy of the sources: an incremental build never fails where a clean build
 * passes. A schema edit that drifts a resolver fails the incremental build with the code and the coordinate, and the repair
 * passes again.</p>
 */
abstract class IncrementalCompilationContract {

    private static final int FILLERS = 20;

    /** The directory name of the sample under {@code samples}. */
    abstract String sample();

    /** The tasks that build the classes. */
    String buildTask() {
        return "classes";
    }

    /** The language directory and the file extension of the sources. */
    abstract String language();

    abstract String extension();

    /** The line of the build file that puts the processor on the path of the compiler, which the control build removes. */
    abstract String processorDependency();

    /** The class file directories of the project that a compile writes, relative to the project. */
    List<String> outputs() {
        return List.of("build/classes");
    }

    /** The source of a bean class whose only member returns {@code value}; {@code annotation} is the simple name of the stereotype. */
    abstract String bean(String annotation, int number, int value);

    /** An edit of {@code Book} that changes its binary interface. */
    abstract void changeBookInterface(Path book);

    /** Whether a body-only edit of a class that is not a resolver makes the build visit no resolver (no note of the visitor). */
    boolean zeroVisitsOnBodyEdit() {
        return false;
    }

    @Test
    void theClassesRewrittenWithTheProcessorAreTheClassesRewrittenWithout() {
        GradleSample with = GradleSample.copyOf(sample(), "incremental-processor");
        GradleSample control = GradleSample.copyOf(sample(), "incremental-control");
        Files.replace(control.file("build.gradle.kts"), processorDependency(), "");
        for (GradleSample sample : List.of(with, control)) {
            for (int i = 0; i < FILLERS; i++) {
                Files.write(source(sample, "Filler" + i), bean("Singleton", i, i));
            }
        }

        // the first build of each project starts the daemon and loads the compiler; the recorded clean build is the second one
        with.build(tasks());
        control.build(tasks());
        with.build("clean");
        control.build("clean");
        long cleanWith = timed(() -> with.build(tasks()));
        long cleanControl = timed(() -> control.build(tasks()));
        Report.record(sample() + ": clean compile of " + (FILLERS + 2) + " classes, with the processor " + cleanWith
                + " ms, control without " + cleanControl + " ms (" + environment() + ")");

        int step = 0;
        step = edit(with, control, step, "the body of a bean that is not a resolver", true, sample -> Files.write(
                source(sample, "Filler3"), bean("Singleton", 3, 303)));
        step = edit(with, control, step, "the body of the resolver", false, sample -> Files.replace(
                source(sample, "BookQuery"), "First", "Second"));
        step = edit(with, control, step, "the binary interface of Book, which the resolver uses", false,
                sample -> changeBookInterface(source(sample, "Book")));
        step = edit(with, control, step, "a new class with a stereotype that the visitor does not claim", false,
                sample -> Files.write(source(sample, "Filler20"), bean("Prototype", 20, 20)));
        edit(with, control, step, "a deleted class", false, sample -> {
            try {
                java.nio.file.Files.delete(source(sample, "Filler11"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    void aSchemaEditFailsTheIncrementalBuildWithTheCoordinateAndTheRepairPasses() {
        GradleSample sample = GradleSample.copyOf(sample(), "incremental-schema");
        Path schema = sample.file(Expect.SCHEMA);
        sample.build(tasks());

        Files.replace(schema, Expect.BOUND_ARGUMENT, Expect.RENAMED_ARGUMENT);
        BuildResult drifted = sample.buildAndFail(tasks());
        Expect.diagnostic(drifted.getOutput(), "argument-mismatch", "Query.books");
        Expect.schemaLocation(drifted.getOutput());
        assertCleanBuildFails(sample, "drift");

        Files.replace(schema, Expect.RENAMED_ARGUMENT, Expect.BOUND_ARGUMENT);
        BuildResult repaired = sample.build(tasks());
        assertFalse(repaired.getOutput().contains("[argument-mismatch]"), repaired.getOutput());
        assertCleanBuildPasses(sample, "repair");
    }

    private int edit(GradleSample with, GradleSample control, int step, String what, boolean bodyOfNonResolver,
                     java.util.function.Consumer<GradleSample> change) {
        Map<String, String> before = snapshot(with);
        Map<String, String> controlBefore = snapshot(control);
        change.accept(with);
        change.accept(control);
        BuildResult[] result = new BuildResult[1];
        long withMillis = timed(() -> result[0] = with.build(tasks()));
        long controlMillis = timed(() -> control.build(tasks()));
        Set<String> rewritten = rewritten(before, snapshot(with));
        Set<String> controlRewritten = rewritten(controlBefore, snapshot(control));

        Report.record(sample() + ": incremental compile after an edit of " + what + ": " + rewritten.size()
                + " class files rewritten with the processor, " + controlRewritten.size() + " by the control; "
                + withMillis + " ms with, " + controlMillis + " ms without");
        assertEquals(controlRewritten, rewritten, "the edit of " + what + " rewrites what the control rewrites");
        assertFalse(rewritten.isEmpty(), "the edit of " + what + " is compiled");
        if (bodyOfNonResolver && zeroVisitsOnBodyEdit()) {
            assertFalse(result[0].getOutput().contains("checked "),
                    "an edit of the body of a class that is not a resolver visits no resolver:\n" + result[0].getOutput());
        }
        assertCleanBuildPasses(with, "step" + step);
        return step + 1;
    }

    private void assertCleanBuildPasses(GradleSample incremental, String scenario) {
        cleanCopy(incremental, scenario).build(tasks());
    }

    private void assertCleanBuildFails(GradleSample incremental, String scenario) {
        cleanCopy(incremental, scenario).buildAndFail(tasks());
    }

    // the sources and the schema of the incremental project, built from scratch: what a clean build says about the same state
    private GradleSample cleanCopy(GradleSample incremental, String scenario) {
        GradleSample copy = GradleSample.copyOf(sample(), "incremental-clean-" + scenario);
        Files.deleteTree(copy.file("src"));
        Files.copyTree(incremental.file("src"), copy.file("src"));
        return copy;
    }

    private Path source(GradleSample sample, String name) {
        return sample.file("src/main/" + language() + "/example/" + name + "." + extension());
    }

    private String[] tasks() {
        return new String[] {buildTask(), "--no-build-cache"};
    }

    /** A class file and its modification time and size: an incremental compile that rewrites a class changes one of the two. */
    private Map<String, String> snapshot(GradleSample sample) {
        Map<String, String> files = new TreeMap<>();
        for (String output : outputs()) {
            Path root = sample.file(output);
            if (!java.nio.file.Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = java.nio.file.Files.walk(root)) {
                for (Path file : (Iterable<Path>) walk.filter(java.nio.file.Files::isRegularFile)::iterator) {
                    files.put(sample.dir().relativize(file).toString(),
                            java.nio.file.Files.getLastModifiedTime(file).toMillis() + "/" + java.nio.file.Files.size(file));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    private static Set<String> rewritten(Map<String, String> before, Map<String, String> after) {
        Set<String> changed = new TreeSet<>();
        after.forEach((file, state) -> {
            if (!state.equals(before.get(file))) {
                changed.add(file);
            }
        });
        before.keySet().stream().filter(file -> !after.containsKey(file)).forEach(file -> changed.add(file + " (deleted)"));
        return changed;
    }

    private static long timed(Runnable build) {
        long start = System.nanoTime();
        build.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static String environment() {
        return "JDK " + Runtime.version() + ", " + Runtime.getRuntime().availableProcessors() + " processors, load average "
                + ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    }
}
