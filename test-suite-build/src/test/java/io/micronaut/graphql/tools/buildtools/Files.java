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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Path;

/** The few file operations of the specs. */
final class Files {

    private Files() {
    }

    static void copyTree(Path from, Path to) {
        try {
            java.nio.file.Files.walkFileTree(from, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    java.nio.file.Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    java.nio.file.Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void deleteTree(Path root) {
        if (!java.nio.file.Files.exists(root)) {
            return;
        }
        try {
            java.nio.file.Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    java.nio.file.Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    java.nio.file.Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String read(Path file) {
        try {
            return java.nio.file.Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void write(Path file, String text) {
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Replaces the text once; fails when it is not there, so that a sample that changed cannot make an edit a no-op. */
    static void replace(Path file, String from, String to) {
        String text = read(file);
        if (!text.contains(from)) {
            throw new IllegalStateException(file + " does not contain: " + from);
        }
        write(file, text.replace(from, to));
    }

    /** Removes the lines from the one that holds {@code tag::name[]} to the one that holds {@code end::name[]}, both included. */
    static void removeTagged(Path file, String tag) {
        String begin = "tag::" + tag + "[]";
        String end = "end::" + tag + "[]";
        StringBuilder kept = new StringBuilder();
        boolean inside = false;
        boolean found = false;
        for (String line : read(file).split("\n", -1)) {
            if (!inside && line.contains(begin)) {
                inside = true;
                found = true;
            }
            if (!inside) {
                kept.append(line).append('\n');
            }
            if (inside && line.contains(end)) {
                inside = false;
            }
        }
        if (!found || inside) {
            throw new IllegalStateException(file + " has no complete tagged region " + tag);
        }
        write(file, kept.substring(0, kept.length() - 1));
    }
}
