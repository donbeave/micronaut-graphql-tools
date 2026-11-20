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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The retry decision of a Maven run, on stub outputs: no network and no {@code mvn}. */
class MavenSampleRetryTest {

    private static final String BUILD_FAILURE_TAIL = "[ERROR] BUILD FAILURE\n[ERROR] Failed to execute goal on project sample\n";

    @ParameterizedTest
    @ValueSource(strings = {
            "[ERROR] Could not transfer artifact io.micronaut:micronaut-core:pom:4.10.0 from/to central (https://repo.maven.apache.org)\n",
            "[FATAL] Non-resolvable parent POM for example:groovy-maven:0.1: Could not transfer artifact io.micronaut.platform:micronaut-parent\n",
            "[ERROR] Failed to execute goal on project sample: Could not resolve dependencies for project example:sample\n",
            "[ERROR] Received status code 429 from https://repo.maven.apache.org/maven2/io/micronaut\n",
            "[ERROR] Received status code 503 from https://repo.maven.apache.org/maven2/io/micronaut\n",
            "[ERROR] Connection reset\n",
            "[ERROR] Read timed out\n"
    })
    void aDownloadFailureIsRetried(String transientLine) {
        assertTrue(MavenSample.isTransientFailure(1, transientLine + BUILD_FAILURE_TAIL));
    }

    @Test
    void aSuccessfulRunIsNotRetried() {
        assertFalse(MavenSample.isTransientFailure(0, "[ERROR] Could not transfer artifact\n[INFO] BUILD SUCCESS\n"));
    }

    @Test
    void aFailureWithoutADownloadSignatureIsNotRetried() {
        assertFalse(MavenSample.isTransientFailure(1, BUILD_FAILURE_TAIL));
    }

    @Test
    void aCompilationErrorIsNotRetriedEvenWithADownloadSignature() {
        String output = "[ERROR] COMPILATION ERROR :\n[ERROR] Could not transfer artifact io.micronaut:micronaut-core:pom:4.10.0\n"
                + BUILD_FAILURE_TAIL;
        assertFalse(MavenSample.isTransientFailure(1, output));
    }

    @Test
    void aTestFailureIsNotRetriedEvenWithADownloadSignature() {
        String output = "[ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0\n[ERROR] Read timed out\n"
                + "[ERROR] There are test failures.\n" + BUILD_FAILURE_TAIL;
        assertFalse(MavenSample.isTransientFailure(1, output));
    }

    @Test
    void aTestErrorLineIsNotRetriedEvenWithADownloadSignature() {
        String output = "BookQueryTest.books  <<< ERROR!\nConnection reset\n" + BUILD_FAILURE_TAIL;
        assertFalse(MavenSample.isTransientFailure(1, output));
    }

    @Test
    void aProcessorDiagnosticIsNotRetried() {
        String output = "[ERROR] /work/src/main/java/BookQuery.java:[12,5] [argument-mismatch] Query.books: the argument limit is not in the schema\n"
                + BUILD_FAILURE_TAIL;
        assertFalse(MavenSample.isTransientFailure(1, output));
    }

    @Test
    void aProcessorDiagnosticIsNotRetriedEvenWithADownloadSignature() {
        String output = "[ERROR] [processor-schema-not-configured] -: the option is missing\n[ERROR] Received status code 429\n"
                + BUILD_FAILURE_TAIL;
        assertFalse(MavenSample.isTransientFailure(1, output));
    }
}
