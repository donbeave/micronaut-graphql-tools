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

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every Maven sample proves: the option reaches the compiler, a drifted class fails {@code mvn
 * verify} with its code and coordinate, a missing option fails, and {@code checks=false} compiles with one note. Maven does
 * not track the schema directory: the compiler plugin of a Java project treats the classes as up to date after a schema
 * edit, so the documented rule is {@code mvn clean verify}; what each build does without {@code clean} is recorded.
 */
abstract class MavenSampleContract {

    /** The directory name of the sample under {@code samples}. */
    abstract String sample();

    /** Whether a build without {@code clean} still sees the schema edit (the Java compiler plugin does not). */
    abstract boolean staleWithoutClean();

    @Test
    void theOptionArrivesAndADriftedClassFailsAfterClean() {
        MavenSample sample = MavenSample.copyOf(sample(), "drift");
        Path schema = sample.file(Expect.SCHEMA);

        MavenSample.Result first = sample.run("verify");
        assertEquals(0, first.exitCode(), first.output());
        Expect.checked(first.output(), schema.getParent(), 0);

        Files.replace(schema, Expect.BOUND_ARGUMENT, Expect.RENAMED_ARGUMENT);
        MavenSample.Result withoutClean = sample.run("verify");
        Report.record(sample() + ": a drifted schema without clean " + (withoutClean.exitCode() == 0 ? "passes (stale)" : "fails"));
        if (staleWithoutClean()) {
            assertEquals(0, withoutClean.exitCode(), "the compiler plugin does not see a schema edit:\n" + withoutClean.output());
        } else {
            assertNotEquals(0, withoutClean.exitCode(), withoutClean.output());
        }

        MavenSample.Result drifted = sample.run("clean", "verify");
        assertNotEquals(0, drifted.exitCode(), drifted.output());
        Expect.diagnostic(drifted.output(), "argument-mismatch", "Query.books");
        Expect.schemaLocation(drifted.output());

        Files.replace(schema, Expect.RENAMED_ARGUMENT, Expect.BOUND_ARGUMENT);
        MavenSample.Result repaired = sample.run("clean", "verify");
        assertEquals(0, repaired.exitCode(), repaired.output());
    }

    @Test
    void aMissingOptionFails() {
        MavenSample sample = MavenSample.copyOf(sample(), "missing-option");
        Files.removeTagged(sample.file("pom.xml"), "schema-dirs");

        MavenSample.Result failed = sample.run("verify");

        assertNotEquals(0, failed.exitCode(), failed.output());
        Expect.diagnostic(failed.output(), "processor-schema-not-configured", "-");
    }

    @Test
    void checksFalsePrintsItsNoteAndPasses() {
        MavenSample sample = MavenSample.copyOf(sample(), "checks-off");
        Files.replace(sample.file(Expect.SCHEMA), Expect.BOUND_ARGUMENT, Expect.RENAMED_ARGUMENT);

        MavenSample.Result passed = sample.run("verify", "-Dgraphql.checks=false");

        assertEquals(0, passed.exitCode(), passed.output());
        Expect.checksOff(passed.output());
        assertTrue(passed.output().contains("BUILD SUCCESS"), passed.output());
    }
}
