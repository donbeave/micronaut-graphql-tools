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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Compares generated files, or any text such as an IR snapshot, with the snapshots under {@code golden/<case>/}.
 *
 * <p>The snapshot root is the system property {@value #DIRECTORY_PROPERTY} (the build sets it to
 * {@code src/test/resources/golden}). With the system property {@value #UPDATE_PROPERTY} set to {@code true}, which is what
 * {@code -PupdateGolden} does, the snapshots of a case are rewritten and the check passes. A snapshot with no generated file,
 * a generated file with no snapshot and a changed file each fail with a diff.</p>
 */
public final class GoldenFiles {

    public static final String DIRECTORY_PROPERTY = "codegen.golden.dir";
    public static final String UPDATE_PROPERTY = "codegen.golden.update";

    private static final int CONTEXT = 3;

    private final Path root;
    private final boolean update;

    /**
     * Creates the harness from the system properties.
     */
    public GoldenFiles() {
        this(rootFromProperty(), Boolean.getBoolean(UPDATE_PROPERTY));
    }

    /**
     * Creates the harness.
     *
     * @param root   the directory that holds one directory per case
     * @param update whether to rewrite the snapshots instead of failing
     */
    public GoldenFiles(Path root, boolean update) {
        this.root = root;
        this.update = update;
    }

    private static Path rootFromProperty() {
        String value = System.getProperty(DIRECTORY_PROPERTY);
        if (value == null) {
            throw new IllegalStateException("The system property " + DIRECTORY_PROPERTY + " is not set");
        }
        return Path.of(value);
    }

    /**
     * Compares the files of a case with its snapshots.
     *
     * @param caseName the name of the directory under the snapshot root
     * @param files    the generated files
     */
    public void assertMatches(String caseName, List<GeneratedFile> files) {
        Map<String, String> actual = new TreeMap<>();
        for (GeneratedFile file : files) {
            if (actual.put(file.path(), file.content()) != null) {
                throw new AssertionError("The case " + caseName + " generated " + file.path() + " twice");
            }
        }
        Path directory = root.resolve(caseName);
        if (update) {
            write(directory, actual);
            return;
        }
        Map<String, String> expected = read(directory);
        Set<String> paths = new TreeSet<>(expected.keySet());
        paths.addAll(actual.keySet());
        StringBuilder failure = new StringBuilder();
        for (String path : paths) {
            if (!actual.containsKey(path)) {
                failure.append("Missing generated file ").append(path).append(" (a snapshot exists)\n");
            } else if (!expected.containsKey(path)) {
                failure.append("Unexpected generated file ").append(path).append(" (no snapshot)\n");
            } else if (!expected.get(path).equals(actual.get(path))) {
                failure.append("Changed file ").append(path).append('\n').append(diff(expected.get(path), actual.get(path)));
            }
        }
        if (!failure.isEmpty()) {
            throw new AssertionError("The output of the case " + caseName + " differs from " + directory
                    + ". Review the diff, then refresh the snapshots with -PupdateGolden.\n" + failure);
        }
    }

    /**
     * Compares one text with its snapshot, for example the text form of an IR.
     *
     * @param caseName the name of the directory under the snapshot root
     * @param path     the file name inside the case
     * @param text     the actual text
     */
    public void assertText(String caseName, String path, String text) {
        assertMatches(caseName, List.of(new GeneratedFile(path, text)));
    }

    /**
     * A line diff with {@value #CONTEXT} lines of context, {@code -} for the snapshot and {@code +} for the generated text.
     *
     * @param expected the snapshot
     * @param actual   the generated text
     * @return the diff
     */
    static String diff(String expected, String actual) {
        List<String> a = List.of(expected.split("\n", -1));
        List<String> b = List.of(actual.split("\n", -1));
        int prefix = 0;
        while (prefix < a.size() && prefix < b.size() && a.get(prefix).equals(b.get(prefix))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < a.size() - prefix && suffix < b.size() - prefix
                && a.get(a.size() - 1 - suffix).equals(b.get(b.size() - 1 - suffix))) {
            suffix++;
        }
        StringBuilder out = new StringBuilder();
        out.append("@@ line ").append(prefix + 1).append(" @@\n");
        for (int i = Math.max(0, prefix - CONTEXT); i < prefix; i++) {
            out.append("  ").append(a.get(i)).append('\n');
        }
        for (int i = prefix; i < a.size() - suffix; i++) {
            out.append("- ").append(a.get(i)).append('\n');
        }
        for (int i = prefix; i < b.size() - suffix; i++) {
            out.append("+ ").append(b.get(i)).append('\n');
        }
        for (int i = a.size() - suffix; i < Math.min(a.size(), a.size() - suffix + CONTEXT); i++) {
            out.append("  ").append(a.get(i)).append('\n');
        }
        return out.toString();
    }

    private static Map<String, String> read(Path directory) {
        Map<String, String> files = new TreeMap<>();
        if (!Files.isDirectory(directory)) {
            return files;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(directory.relativize(file).toString().replace('\\', '/'), Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    private static void write(Path directory, Map<String, String> files) {
        try {
            if (Files.isDirectory(directory)) {
                try (Stream<Path> walk = Files.walk(directory)) {
                    List<Path> existing = new ArrayList<>(walk.sorted(Comparator.reverseOrder()).toList());
                    for (Path path : existing) {
                        Files.delete(path);
                    }
                }
            }
            for (Map.Entry<String, String> file : files.entrySet()) {
                Path target = directory.resolve(file.getKey()).normalize();
                if (!target.startsWith(directory)) {
                    throw new IllegalArgumentException("The generated path leaves the case directory: " + file.getKey());
                }
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
