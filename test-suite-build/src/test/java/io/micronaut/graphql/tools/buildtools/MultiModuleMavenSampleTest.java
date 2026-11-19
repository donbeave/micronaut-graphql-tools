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
 * A reactor with a schema module and two modules with resolvers. Maven compiles every module of the reactor in one JVM, so the
 * options that Micronaut copies into system properties reach the modules compiled after the one that set them.
 */
class MultiModuleMavenSampleTest {

    private static final String SAMPLE = "multi-module-maven";

    @Test
    void everyModuleIsCheckedAgainstTheSchemaModule() {
        MavenSample sample = MavenSample.copyOf(SAMPLE, "build");

        MavenSample.Result built = sample.run("verify");

        assertEquals(0, built.exitCode(), built.output());
        for (String module : new String[] {"authors", "books"}) {
            Path schemaDirectory = sample.file(module + "/../schema/src/main/resources/graphql");
            String note = "checked 1 resolver class against 1 schema file in [" + schemaDirectory + "]; 0 findings.";
            assertTrue(built.output().contains(note), "the build prints the note '" + note + "':\n" + built.output());
        }
    }

    /** The module that forgot the option is built after a module that set it, in the same Maven JVM. */
    @Test
    void aMissingOptionIsNotFilledInByAnEarlierModuleOfTheReactor() {
        MavenSample sample = MavenSample.copyOf(SAMPLE, "missing-option-after-module");
        Files.removeTagged(sample.file("books/pom.xml"), "schema-dirs");

        MavenSample.Result failed = sample.run("verify");

        assertNotEquals(0, failed.exitCode(), failed.output());
        assertTrue(failed.output().contains("Building authors") && failed.output().indexOf("Building authors") < failed.output().indexOf("Building books"),
                "authors is built before books:\n" + failed.output());
        Expect.diagnostic(failed.output(), "processor-schema-not-configured", "-");
        assertTrue(failed.output().contains("books/target/classes"), "the error names the module:\n" + failed.output());
    }
}
