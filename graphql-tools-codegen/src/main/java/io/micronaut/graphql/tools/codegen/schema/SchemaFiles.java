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
package io.micronaut.graphql.tools.codegen.schema;

import io.micronaut.graphql.tools.codegen.Diagnostic;
import io.micronaut.graphql.tools.codegen.Origin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Finds and reads schema files. The path of a source is relative to the schema root with {@code /} separators, so the origin of a
 * definition does not depend on where a checkout lives.
 */
final class SchemaFiles {

    private SchemaFiles() {
    }

    static List<SchemaSource> readDirectory(Path directory, List<String> includes, List<String> excludes,
                                            List<Diagnostic> diagnostics) {
        return readDirectory(Kind.SCHEMA, directory, includes, excludes, diagnostics);
    }

    static List<SchemaSource> readDirectory(Kind kind, Path directory, List<String> includes, List<String> excludes,
                                            List<Diagnostic> diagnostics) {
        String shown = directory.toString().replace('\\', '/');
        if (!Files.isDirectory(directory)) {
            diagnostics.add(fileProblem(kind.unreadable(), shown,
                    "The " + kind.what() + " directory " + shown + " does not exist or is not a directory."));
            return List.of();
        }
        List<Path> files;
        try {
            files = SchemaFileSelector.select(directory, includes, excludes);
        } catch (UncheckedIOException e) {
            diagnostics.add(fileProblem(kind.unreadable(), shown,
                    "The " + kind.what() + " directory " + shown + " cannot be listed: " + e.getMessage()));
            return List.of();
        }
        List<SchemaSource> sources = new ArrayList<>();
        int before = diagnostics.size();
        files.forEach(file -> read(kind, file, relative(directory, file), sources, diagnostics));
        if (sources.isEmpty() && diagnostics.size() == before) {
            diagnostics.add(Diagnostic.optionError(kind.none(), kind.includesName(),
                    "No " + kind.what() + " file under " + shown + " matches the option " + kind.includesName() + " " + includes
                            + (excludes.isEmpty() ? "" : " and is not removed by the option "
                            + kind.excludesName() + " " + excludes) + "."));
        }
        return sources;
    }

    static List<SchemaSource> readFiles(Collection<Path> files, List<Diagnostic> diagnostics) {
        return readFiles(Kind.SCHEMA, files, diagnostics);
    }

    static List<SchemaSource> readFiles(Kind kind, Collection<Path> files, List<Diagnostic> diagnostics) {
        Set<Path> unique = new LinkedHashSet<>();
        files.forEach(file -> unique.add(file.toAbsolutePath().normalize()));
        Path root = commonParent(unique);
        Map<String, Path> byPath = new TreeMap<>(SchemaLoader.UTF8_ORDER);
        for (Path file : unique) {
            byPath.put(root == null ? file.toString().replace('\\', '/') : relative(root, file), file);
        }
        List<SchemaSource> sources = new ArrayList<>();
        byPath.forEach((path, file) -> read(kind, file, path, sources, diagnostics));
        return sources;
    }

    private static void read(Kind kind, Path file, String relative, List<SchemaSource> sources, List<Diagnostic> diagnostics) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            sources.add(new SchemaSource(relative, text));
        } catch (IOException | RuntimeException e) {
            diagnostics.add(fileProblem(kind.unreadable(), relative,
                    "The " + kind.what() + " file " + relative + " cannot be read: " + e.getMessage()));
        }
    }

    private static Diagnostic fileProblem(String code, String path, String message) {
        return new Diagnostic(Diagnostic.Severity.ERROR, code, message, null, new Origin(path, 0, 0));
    }

    private static String relative(Path root, Path file) {
        return SchemaFileSelector.relative(root, file);
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

    /**
     * What a file is, for the messages and the codes: a schema file or an operation document.
     *
     * @param what         the name in a message
     * @param unreadable   the code of a file that cannot be read
     * @param none         the code of a directory without a selected file
     * @param includesName the name of the option that selects the files of a directory
     * @param excludesName the name of the option that removes files from the selection
     */
    record Kind(String what, String unreadable, String none, String includesName, String excludesName) {

        static final Kind SCHEMA = new Kind("schema", Diagnostic.SCHEMA_FILE_UNREADABLE, Diagnostic.NO_SCHEMA_FILES, "includes",
                "excludes");
        static final Kind OPERATION = new Kind("operation", Diagnostic.OPERATION_FILE_UNREADABLE, Diagnostic.NO_OPERATION_DOCUMENTS,
                "operationIncludes", "operationExcludes");
    }
}
