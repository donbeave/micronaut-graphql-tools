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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroovyMavenPluginSampleTest extends MavenPluginSampleContract {

    @Override
    String sample() {
        return "maven-plugin-groovy";
    }

    /**
     * GMavenPlus compiles the {@code sources} it is configured with and does not read the compile source roots, so the generated
     * directory has to be a source of its configuration: the root that the goal adds is not enough. The documented snippet has it.
     */
    @Test
    void theGeneratedDirectoryMustBeASourceOfTheGMavenPlusConfiguration() {
        MavenSample sample = MavenSample.copyOf(sample(), "sources");
        Files.removeTagged(sample.file("pom.xml"), "groovy-sources");

        MavenSample.Result without = sample.run("compile");

        assertNotEquals(0, without.exitCode(), without.output());
        assertTrue(without.output().contains("package placed does not exist") || without.output().contains("unable to resolve class placed.Placed"),
                without.output());
    }
}
