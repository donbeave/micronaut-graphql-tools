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

/** The schema, the resolvers and the application are three modules; only the module with the resolvers applies the processor. */
class MultiModuleGradleSampleTest extends GradleSampleContract {

    @Override
    String sample() {
        return "multi-module-gradle";
    }

    @Override
    String checkedTask() {
        return ":feature:compileJava";
    }

    @Override
    String buildTask() {
        return ":app:classes";
    }

    @Override
    String schemaFile() {
        return "schema/src/main/resources/graphql/schema.graphqls";
    }

    @Override
    String buildFile() {
        return "feature/build.gradle.kts";
    }

    @Test
    void aSchemaEditRecompilesTheResolverModuleAndNotTheApplication() {
        GradleSample sample = GradleSample.copyOf(sample(), "schema-edit-modules");
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(sample.build(buildTask()), checkedTask()));

        Files.replace(sample.file(schemaFile()), "type Book {", "# a comment that changes the file\ntype Book {");
        BuildResult edited = sample.build(buildTask());

        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(edited, checkedTask()));
        assertEquals(TaskOutcome.UP_TO_DATE, GradleSample.outcome(edited, ":app:compileJava"),
                "the application does not apply the processor, so a schema edit leaves it alone");
    }
}
