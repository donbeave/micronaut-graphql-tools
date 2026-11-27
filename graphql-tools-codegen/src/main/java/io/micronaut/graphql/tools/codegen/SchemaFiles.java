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
package io.micronaut.graphql.tools.codegen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds and reads schema files. The path of a source is relative to the schema root with {@code /} separators, so the origin of a
 * definition does not depend on where a checkout lives.
 */
final class SchemaFiles {

    private SchemaFiles() {
    }

    static List<SchemaSource> readDirectory(Path directory, List<String> includes, List<String> excludes,
                                            List<Diagnostic> diagnostics) {
        if (!Files.isDirectory(directory)) {
            diagnostics.add(fileProblem(Diagnostic.SCHEMA_FILE_UNREADABLE, directory.toString().replace('\\', '/'),
                    "The schema directory " + directory.toString().replace('\\', '/') + " does not exist or is not a directory."));
            return List.of();
        }
        List<Pattern> include = Globs.compile(includes);
        List<Pattern> exclude = Globs.compile(excludes);
        List<Path> files;
        try (Stream<Path> paths = Files.walk(directory)) {
            files = paths.filter(Files::isRegularFile).toList();
        } catch (IOException | UncheckedIOException e) {
            diagnostics.add(fileProblem(Diagnostic.SCHEMA_FILE_UNREADABLE, directory.toString().replace('\\', '/'),
                    "The schema directory " + directory.toString().replace('\\', '/') + " cannot be listed: " + e.getMessage()));
            return List.of();
        }
        List<SchemaSource> sources = new ArrayList<>();
        int before = diagnostics.size();
        for (Path file : files) {
            String relative = relative(directory, file);
            if (Globs.matchesAny(include, relative) && !Globs.matchesAny(exclude, relative)) {
                read(file, relative, sources, diagnostics);
            }
        }
        if (sources.isEmpty() && diagnostics.size() == before) {
            diagnostics.add(Diagnostic.optionError(Diagnostic.NO_SCHEMA_FILES, "includes",
                    "No schema file under " + directory.toString().replace('\\', '/') + " matches the option includes " + includes
                            + (excludes.isEmpty() ? "" : " and is not removed by the option excludes " + excludes) + "."));
        }
        return sources;
    }

    static List<SchemaSource> readFiles(Collection<Path> files, List<Diagnostic> diagnostics) {
        Set<Path> unique = new LinkedHashSet<>();
        files.forEach(file -> unique.add(file.toAbsolutePath().normalize()));
        Path root = commonParent(unique);
        List<SchemaSource> sources = new ArrayList<>();
        for (Path file : unique) {
            read(file, root == null ? file.toString().replace('\\', '/') : relative(root, file), sources, diagnostics);
        }
        return sources;
    }

    private static void read(Path file, String relative, List<SchemaSource> sources, List<Diagnostic> diagnostics) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            sources.add(new SchemaSource(relative, text.startsWith("﻿") ? text.substring(1) : text));
        } catch (IOException | RuntimeException e) {
            diagnostics.add(fileProblem(Diagnostic.SCHEMA_FILE_UNREADABLE, relative,
                    "The schema file " + relative + " cannot be read: " + e.getMessage()));
        }
    }

    private static Diagnostic fileProblem(String code, String path, String message) {
        return new Diagnostic(Diagnostic.Severity.ERROR, code, message, null, new Origin(path, 0, 0));
    }

    private static String relative(Path root, Path file) {
        StringBuilder path = new StringBuilder();
        for (Path part : root.relativize(file)) {
            path.append(path.length() == 0 ? "" : "/").append(part);
        }
        return path.toString();
    }

    private static Path commonParent(Collection<Path> files) {
        Path common = null;
        for (Path file : files) {
            Path parent = file.getParent();
            if (parent == null) {
                return null;
            }
            if (common == null) {
                common = parent;
                continue;
            }
            while (common != null && !parent.startsWith(common)) {
                common = common.getParent();
            }
            if (common == null) {
                return null;
            }
        }
        return common;
    }
}
