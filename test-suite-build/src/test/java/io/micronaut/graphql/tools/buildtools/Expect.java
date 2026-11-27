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

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the build output of a sample must contain, the same in every tool. */
final class Expect {

    /** The schema of every sample, and the edit that makes {@code BookQuery} drift from it. */
    static final String SCHEMA = "src/main/resources/graphql/schema.graphqls";
    static final String BOUND_ARGUMENT = "books(limit: Int)";
    static final String RENAMED_ARGUMENT = "books(max: Int)";

    /** The client schema of every sample, its operation, and the edit that makes the operation drift from the schema. */
    static final String CLIENT_SCHEMA = "src/main/graphql/client/catalog/schema.graphqls";
    static final String CLIENT_OPERATION = "src/main/graphql/client/catalog/books.graphql";
    static final String BOUND_FIELD = "    title\n";
    static final String RENAMED_FIELD = "    name\n";

    private Expect() {
    }

    /** The note of the visitor names the schema directory of the build, so the option arrived with the right value. */
    static void checked(String output, Path schemaDirectory, int findings) {
        String note = "checked 1 resolver class against 1 schema file in [" + schemaDirectory + "]; "
                + findings + (findings == 1 ? " finding." : " findings.");
        assertTrue(output.contains(note), "the build prints the note '" + note + "':\n" + output);
    }

    /** The note of the client visitor: the operations were validated against the client schema. */
    static void clientChecked(String output) {
        String note = "checked 1 client operation against 1 client schema.";
        assertTrue(output.contains(note), "the build prints the note '" + note + "':\n" + output);
    }

    static void clientChecksOff(String output) {
        assertTrue(output.contains("GraphQL client checks are off (micronaut.graphql.tools.processor.client.checks=false)"),
                "the build prints the note that the client checks are off:\n" + output);
        assertFalse(output.contains("[client-document-invalid]"), output);
    }

    static void diagnostic(String output, String code, String coordinate) {
        String line = "[" + code + "] " + coordinate + ":";
        assertTrue(output.contains(line), "the build prints '" + line + "':\n" + output);
    }

    static void schemaLocation(String output) {
        assertTrue(output.contains("graphql/schema.graphqls:3:3"), "the diagnostic names the schema file, line and column:\n" + output);
    }

    static void checksOff(String output) {
        assertTrue(output.contains("GraphQL compile-time checks are off (micronaut.graphql.tools.processor.checks=false)"),
                "the build prints the note that the checks are off:\n" + output);
        assertFalse(output.contains("[argument-mismatch]"), output);
    }
}
