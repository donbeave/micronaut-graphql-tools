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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outcome that {@code looseNullability} changes: {@code BookQuery.book} returns a non-null {@code Book} for a nullable field,
 * a Kotlin type stricter than the schema type. The check is off by default, a warning with {@code warn} and a compile error with
 * {@code fail}. Only KSP reads Kotlin nullability: under kapt a class is read as Java stubs, so the Maven samples cannot show it.
 */
class KotlinKspLooseNullabilityTest {

    private static final String LOOSE = "kotlin-nullability-loose";
    private static final String COORDINATE = "Query.book";

    @Test
    void passesWithoutTheOptionAndFailsWithFail() {
        GradleSample sample = GradleSample.copyOf("kotlin-ksp", "loose-default");
        Files.removeTagged(sample.file("build.gradle.kts"), "loose-nullability");

        BuildResult passed = sample.build("classes", "--info");
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(passed, ":kspKotlin"));
        assertFalse(passed.getOutput().contains(LOOSE), "the option is off by default:\n" + passed.getOutput());

        GradleSample failing = GradleSample.copyOf("kotlin-ksp", "loose-fail");
        BuildResult failed = failing.buildAndFail("classes", "--info", "-PgraphqlLooseNullability=fail");
        assertEquals(TaskOutcome.FAILED, GradleSample.outcome(failed, ":kspKotlin"));
        Expect.diagnostic(failed.getOutput(), LOOSE, COORDINATE);
    }

    @Test
    void theSnippetDefaultsToIgnoreAndWarnPasses() {
        GradleSample sample = GradleSample.copyOf("kotlin-ksp", "loose-warn");

        BuildResult ignored = sample.build("classes", "--info");
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(ignored, ":kspKotlin"));
        assertFalse(ignored.getOutput().contains(LOOSE), ignored.getOutput());

        BuildResult warned = sample.build("classes", "--info", "-PgraphqlLooseNullability=warn");
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(warned, ":kspKotlin"));
        assertTrue(warned.getOutput().contains(LOOSE) && warned.getOutput().contains(COORDINATE),
                "warn reports the finding without failing:\n" + warned.getOutput());
    }
}
