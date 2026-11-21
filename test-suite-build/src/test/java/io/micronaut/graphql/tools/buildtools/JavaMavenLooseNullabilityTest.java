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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The option in a Maven build: the processor reads it from {@code compilerArgs} (a value that is not {@code fail}, {@code warn} or
 * {@code ignore} fails the build with {@code processor-option-invalid}), and a Java class has no Kotlin nullability, so every valid
 * value passes. The outcome that the option changes is in {@link KotlinKspLooseNullabilityTest}: only KSP reads Kotlin nullability.
 */
class JavaMavenLooseNullabilityTest {

    @Test
    void everyValueOfTheOptionPasses() {
        MavenSample sample = MavenSample.copyOf("java-maven", "loose-values");

        for (String value : new String[] {"ignore", "warn", "fail"}) {
            MavenSample.Result result = sample.run("clean", "verify", "-Dgraphql.looseNullability=" + value);
            assertEquals(0, result.exitCode(), value + ":\n" + result.output());
            Expect.checked(result.output(), sample.file("src/main/resources/graphql"), 0);
        }
    }

    @Test
    void aValueThatIsNotASeverityFails() {
        MavenSample sample = MavenSample.copyOf("java-maven", "loose-invalid");

        MavenSample.Result failed = sample.run("clean", "verify", "-Dgraphql.looseNullability=sometimes");

        assertNotEquals(0, failed.exitCode(), failed.output());
        Expect.diagnostic(failed.output(), "processor-option-invalid", "-");
    }
}
