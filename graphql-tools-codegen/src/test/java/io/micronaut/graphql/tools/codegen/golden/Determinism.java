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
package io.micronaut.graphql.tools.codegen.golden;

import io.micronaut.graphql.tools.codegen.GeneratedFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Checks the determinism contract of generated files: the same bytes on every run and for every order of the schema files,
 * {@code \n} line endings, no timestamp and a {@code @Generated} without a date.
 */
public final class Determinism {

    private static final Pattern TIMESTAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}");
    private static final Pattern GENERATED_WITH_DATE = Pattern.compile("@(?:\\w+\\.)*Generated\\s*\\([^)]*\\bdate\\s*=");
    private static final int SHUFFLES = 5;
    private static final long SEED = 192L;

    private Determinism() {
    }

    /**
     * Runs the generation for the given order and for shuffled orders of the schema files, and compares the outputs
     * byte for byte, in file order too. It then checks each file against the content rules.
     *
     * @param schemaFiles the schema files of the case
     * @param generate    generates the files from the schema files in the given order
     */
    public static void assertDeterministic(List<Path> schemaFiles, Function<List<Path>, List<GeneratedFile>> generate) {
        List<GeneratedFile> first = generate.apply(List.copyOf(schemaFiles));
        assertContent(first);
        assertSame("a second run", first, generate.apply(List.copyOf(schemaFiles)));
        Random random = new Random(SEED);
        for (int i = 0; i < SHUFFLES && schemaFiles.size() > 1; i++) {
            List<Path> shuffled = new ArrayList<>(schemaFiles);
            Collections.shuffle(shuffled, random);
            assertSame("the schema order " + shuffled, first, generate.apply(shuffled));
        }
        List<Path> reversed = new ArrayList<>(schemaFiles);
        Collections.reverse(reversed);
        assertSame("the reversed schema order " + reversed, first, generate.apply(reversed));
    }

    /**
     * Checks the content rules of generated files.
     *
     * @param files the generated files
     */
    public static void assertContent(List<GeneratedFile> files) {
        for (GeneratedFile file : files) {
            if (file.content().indexOf('\r') >= 0) {
                throw new AssertionError(file.path() + " has a carriage return: the line ending is \\n only");
            }
            if (TIMESTAMP.matcher(file.content()).find()) {
                throw new AssertionError(file.path() + " contains a timestamp");
            }
            if (GENERATED_WITH_DATE.matcher(file.content()).find()) {
                throw new AssertionError(file.path() + " has a @Generated with a date");
            }
        }
    }

    private static void assertSame(String what, List<GeneratedFile> expected, List<GeneratedFile> actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("The output of " + what + " differs from the first run: "
                    + expected.stream().map(GeneratedFile::path).toList() + " against "
                    + actual.stream().map(GeneratedFile::path).toList());
        }
    }
}
