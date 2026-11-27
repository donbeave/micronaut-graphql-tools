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

import static org.junit.jupiter.api.Assertions.assertEquals;

class KotlinMavenPluginSampleTest extends MavenPluginSampleContract {

    @Override
    String sample() {
        return "maven-plugin-kotlin";
    }

    /**
     * The snippet lists the generated directory in the {@code sourceDirs} of the Kotlin plugin. This records what the Kotlin plugin of
     * the build does without it: its {@code compile} goal also reads the compile source roots of the project, which the goal of this
     * plugin adds, so the build passes either way; the listing stays in the snippet for the goals and versions that read only
     * {@code sourceDirs}.
     */
    @Test
    void recordsWhetherTheKotlinPluginNeedsTheGeneratedDirectoryInItsSourceDirs() {
        MavenSample sample = MavenSample.copyOf(sample(), "source-dirs");
        Files.replace(sample.file("pom.xml"),
                "<sourceDir>${project.build.directory}/generated-sources/graphql-tools/kotlin</sourceDir>", "");

        MavenSample.Result without = sample.run("clean", "compile");

        Report.record("maven-plugin-kotlin: compile without the generated directory in sourceDirs "
                + (without.exitCode() == 0 ? "passes (the compile source root is read)" : "fails"));
        assertEquals(0, without.exitCode(), without.output());
    }
}
