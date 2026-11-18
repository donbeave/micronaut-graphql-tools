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
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every Gradle sample proves: the option reaches the compiler, a schema edit reruns the compile
 * task, a drifted class fails with its code and coordinate, a missing option fails, and {@code checks=false} compiles
 * with one note. The specs assert what a build prints and which tasks ran, never an internal of the visitor.
 */
abstract class GradleSampleContract {

    /** The directory name of the sample under {@code samples}. */
    abstract String sample();

    /** The path of the task that runs the compile-time checks: the task that compiles the resolvers. */
    abstract String checkedTask();

    /** The task that builds the sample and depends on the checked one. */
    String buildTask() {
        return "classes";
    }

    /** The schema file that the checks read, relative to the sample. */
    String schemaFile() {
        return Expect.SCHEMA;
    }

    /** The build file that holds the snippet of the checked module. */
    String buildFile() {
        return "build.gradle.kts";
    }

    /** Arguments that make the build print the note of the visitor and its errors. */
    String[] arguments() {
        return new String[0];
    }

    /** Whether the cache entry of the checked task is valid at another project path (javac only: the others take the absolute path as an input). */
    boolean relocatable() {
        return false;
    }

    @Test
    void theOptionArrivesAndASchemaEditRerunsTheCheckedTask() {
        GradleSample sample = GradleSample.copyOf(sample(), "schema-edit");
        Path schema = sample.file(schemaFile());

        BuildResult first = sample.build(tasks());
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(first, checkedTask()));
        Expect.checked(first.getOutput(), schema.getParent(), 0);

        BuildResult unchanged = sample.build(tasks());
        assertEquals(TaskOutcome.UP_TO_DATE, GradleSample.outcome(unchanged, checkedTask()),
                "nothing changed, so the checked task is up to date");

        Files.replace(schema, "type Book {", "# a comment that changes the file\ntype Book {");
        BuildResult edited = sample.build(tasks());
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(edited, checkedTask()),
                "a schema edit reruns the checked task, because the build declares the schema as its input");
        Expect.checked(edited.getOutput(), schema.getParent(), 0);

        Files.replace(schema, Expect.BOUND_ARGUMENT, Expect.RENAMED_ARGUMENT);
        BuildResult drifted = sample.buildAndFail(tasks());
        assertEquals(TaskOutcome.FAILED, GradleSample.outcome(drifted, checkedTask()));
        Expect.diagnostic(drifted.getOutput(), "argument-mismatch", "Query.books");
        Expect.schemaLocation(drifted.getOutput());

        Files.replace(schema, Expect.RENAMED_ARGUMENT, Expect.BOUND_ARGUMENT);
        // the repaired project is what the last successful build compiled: the outcome may also be up to date or from the cache
        BuildResult repaired = sample.build(tasks());
        assertNotEquals(TaskOutcome.FAILED, GradleSample.outcome(repaired, checkedTask()));
    }

    @Test
    void aMissingOptionFails() {
        GradleSample sample = GradleSample.copyOf(sample(), "missing-option");
        Files.removeTagged(sample.file(buildFile()), "schema-dirs");

        BuildResult failed = sample.buildAndFail(tasks());

        assertEquals(TaskOutcome.FAILED, GradleSample.outcome(failed, checkedTask()));
        Expect.diagnostic(failed.getOutput(), "processor-schema-not-configured", "-");
    }

    @Test
    void checksFalsePrintsItsNoteAndPasses() {
        GradleSample sample = GradleSample.copyOf(sample(), "checks-off");
        Files.replace(sample.file(schemaFile()), Expect.BOUND_ARGUMENT, Expect.RENAMED_ARGUMENT);

        BuildResult passed = sample.build(tasks("-PgraphqlChecks=false"));

        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(passed, checkedTask()));
        Expect.checksOff(passed.getOutput());
    }

    /** The build cache: a clean rebuild of the same project, and the same project at another path. */
    @Test
    void aCleanRebuildAndARelocatedProjectUseTheBuildCache() {
        GradleSample sample = GradleSample.copyOf(sample(), "build-cache");
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(sample.build(tasks()), checkedTask()));

        sample.build("clean");
        TaskOutcome rebuilt = GradleSample.outcome(sample.build(tasks()), checkedTask());
        GradleSample moved = sample.relocated(sample(), "relocated");
        TaskOutcome relocated = GradleSample.outcome(moved.build(tasks()), checkedTask());

        Report.record(sample() + ": clean rebuild " + rebuilt + ", relocated project " + relocated);
        assertPassed(rebuilt);
        assertPassed(relocated);
        if (relocatable()) {
            assertEquals(TaskOutcome.FROM_CACHE, relocated, "the checked task is relocatable: its schema input is relative");
        }
    }

    private String[] tasks(String... more) {
        String[] extra = arguments();
        String[] all = new String[1 + more.length + extra.length];
        all[0] = buildTask();
        System.arraycopy(more, 0, all, 1, more.length);
        System.arraycopy(extra, 0, all, 1 + more.length, extra.length);
        return all;
    }

    private static void assertPassed(TaskOutcome outcome) {
        assertTrue(outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.FROM_CACHE, "the task passed, not " + outcome);
    }
}
