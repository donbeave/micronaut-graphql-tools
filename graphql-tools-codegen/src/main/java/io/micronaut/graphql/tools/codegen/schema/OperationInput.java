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
import io.micronaut.graphql.tools.codegen.Options;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Where the operation documents of client mode come from: the files of a directory, files that the caller chose, or text that the
 * caller has read. The documents are read in a defined order, the order of their paths in UTF-8 byte order, so the same documents
 * in any order give the same IR.
 *
 * <p>The files of a directory are those whose path relative to the directory matches a glob of
 * {@link Options#operationIncludes()} and no glob of {@link Options#operationExcludes()}, with the rules of the schema files
 * ({@link SchemaFileSelector}). Files and text are used as given, and the globs do not apply to them. Server mode ignores the
 * input.</p>
 */
public final class OperationInput {

    private static final OperationInput NONE = new OperationInput(null, List.of(), List.of());

    private final Path directory;
    private final List<Path> files;
    private final List<SchemaSource> sources;

    private OperationInput(Path directory, List<Path> files, List<SchemaSource> sources) {
        this.directory = directory;
        this.files = files;
        this.sources = sources;
    }

    /**
     * No document: client mode reports that it has no operation.
     *
     * @return the input
     */
    public static OperationInput none() {
        return NONE;
    }

    /**
     * The documents under a directory, selected by the options {@code operationIncludes} and {@code operationExcludes}. The paths
     * in the IR and in the diagnostics are relative to the directory.
     *
     * @param directory the directory
     * @return the input
     */
    public static OperationInput directory(Path directory) {
        return new OperationInput(Objects.requireNonNull(directory, "directory"), List.of(), List.of());
    }

    /**
     * Documents that are files. The paths in the IR and in the diagnostics are relative to the closest directory that holds all of
     * them.
     *
     * @param files the files, in any order
     * @return the input
     */
    public static OperationInput files(Collection<Path> files) {
        return new OperationInput(null, List.copyOf(files), List.of());
    }

    /**
     * Documents that the caller has read.
     *
     * @param sources the documents, in any order; the path of a source is its origin and decides the order
     * @return the input
     */
    public static OperationInput sources(Collection<SchemaSource> sources) {
        return new OperationInput(null, List.of(), List.copyOf(sources));
    }

    List<SchemaSource> read(Options options, List<Diagnostic> diagnostics) {
        if (directory != null) {
            return SchemaFiles.readDirectory(SchemaFiles.Kind.OPERATION, directory, options.operationIncludes(),
                    options.operationExcludes(), diagnostics);
        }
        if (!files.isEmpty()) {
            return SchemaFiles.readFiles(SchemaFiles.Kind.OPERATION, files, diagnostics);
        }
        return sources;
    }
}
