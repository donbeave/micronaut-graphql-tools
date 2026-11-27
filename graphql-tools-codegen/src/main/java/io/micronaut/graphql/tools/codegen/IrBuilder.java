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

import io.micronaut.graphql.tools.codegen.ir.Ir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Builds the {@link Ir} of a schema in two steps. The loader reads the schema files with the graphql-java parser only and merges
 * the definitions and the type extensions in a defined order; the rules then lower the merged definitions and the options into
 * the IR and report the problems as diagnostics. The IR is a pure function of the schema files and the options: the same files in
 * any order give the same IR.
 *
 * <p>Nothing is thrown for the content of the options or of a schema; every problem is a {@link Diagnostic} in the
 * {@link IrResult}, so that one run reports all of them.</p>
 */
public final class IrBuilder {

    private IrBuilder() {
    }

    /**
     * Builds the IR of the schema files under a directory. The files are those whose path relative to the directory matches a glob
     * of {@link Options#includes()} and no glob of {@link Options#excludes()}.
     *
     * @param directory the schema root; the origins in the IR are relative to it
     * @param options   the options
     * @return the IR and the diagnostics
     */
    public static IrResult fromDirectory(Path directory, Options options) {
        Objects.requireNonNull(directory, "directory");
        return build(options, diagnostics -> SchemaFiles.readDirectory(directory, options.includes(), options.excludes(), diagnostics));
    }

    /**
     * Builds the IR of schema files. The options {@code includes} and {@code excludes} are not applied, because the caller chose the
     * files. The origins in the IR are relative to the closest directory that holds all the files.
     *
     * @param files   the files, in any order
     * @param options the options
     * @return the IR and the diagnostics
     */
    public static IrResult fromFiles(Collection<Path> files, Options options) {
        Objects.requireNonNull(files, "files");
        return build(options, diagnostics -> SchemaFiles.readFiles(files, diagnostics));
    }

    /**
     * Builds the IR of schema text that the caller has read.
     *
     * @param sources the sources, in any order
     * @param options the options
     * @return the IR and the diagnostics
     */
    public static IrResult fromSources(Collection<SchemaSource> sources, Options options) {
        Objects.requireNonNull(sources, "sources");
        return build(options, diagnostics -> List.copyOf(sources));
    }

    private static IrResult build(Options options, Function<List<Diagnostic>, List<SchemaSource>> discovery) {
        Objects.requireNonNull(options, "options");
        List<Diagnostic> diagnostics = new ArrayList<>(options.validate());
        List<SchemaSource> sources = discovery.apply(diagnostics);
        SchemaLoader.Loaded loaded = SchemaLoader.load(sources, diagnostics);
        Ir ir = Ir.EMPTY;
        if (diagnostics.stream().noneMatch(Diagnostic::isError)) {
            ir = Lowering.lower(options, loaded.schema(), diagnostics);
            if (diagnostics.stream().anyMatch(Diagnostic::isError)) {
                ir = Ir.EMPTY;
            }
        }
        return new IrResult(ir, diagnostics, loaded.skipped());
    }
}
