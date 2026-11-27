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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Java sample compiles the enum and the name constants that the generator writes for its schema. */
class JavaMavenPluginSampleTest extends MavenPluginSampleContract {

    private static final String MODEL = "target/generated-sources/graphql-tools/java/example/api/model/";
    private static final String NAMES = "target/generated-sources/graphql-tools/java/example/api/names/";

    @Override
    String sample() {
        return "maven-plugin-java";
    }

    @Override
    void assertGeneratedSourcesCompiled(MavenSample sample, MavenSample.Result built) {
        assertTrue(java.nio.file.Files.isRegularFile(sample.file(MODEL + "Color.java")), built.output());
        assertTrue(java.nio.file.Files.isRegularFile(sample.file(NAMES + "QueryNames.java")), built.output());
        assertTrue(java.nio.file.Files.isRegularFile(sample.file("target/classes/example/api/model/Color.class")),
                "the generated enum was compiled with the project:\n" + built.output());
        assertTrue(java.nio.file.Files.isRegularFile(sample.file("target/classes/example/api/names/QueryNames.class")), built.output());
    }

    @Test
    void aSchemaFileThatIsRemovedLeavesNoStaleSource() {
        MavenSample sample = MavenSample.copyOf(sample(), "stale-source");
        Path extra = sample.file("src/main/resources/graphql/extra.graphqls");
        Files.write(extra, "enum Extra {\n  ONE\n}\n");

        MavenSample.Result added = sample.run("compile");
        assertEquals(0, added.exitCode(), added.output());
        assertTrue(java.nio.file.Files.isRegularFile(sample.file(MODEL + "Extra.java")), added.output());
        assertTrue(java.nio.file.Files.isRegularFile(sample.file("target/classes/example/api/model/Extra.class")), added.output());

        Files.deleteTree(extra);
        MavenSample.Result removed = sample.run("compile");
        assertEquals(0, removed.exitCode(), removed.output());
        assertFalse(java.nio.file.Files.exists(sample.file(MODEL + "Extra.java")), "the stale source is deleted:\n" + removed.output());
        assertTrue(java.nio.file.Files.isRegularFile(sample.file(MODEL + "Color.java")), removed.output());
    }
}
