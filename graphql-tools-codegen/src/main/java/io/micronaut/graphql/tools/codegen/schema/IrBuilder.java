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
import io.micronaut.graphql.tools.codegen.Mode;
import io.micronaut.graphql.tools.codegen.Options;
import io.micronaut.graphql.tools.codegen.ir.Ir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Builds the {@link Ir} of a schema in two steps. The loader reads the schema files with the graphql-java parser only and merges
 * the definitions and the type extensions in a defined order; the rules then lower the merged definitions and the options into
 * the IR and report the problems as diagnostics. The IR is a pure function of the schema files and the options: the same files in
 * any order give the same IR.
 *
 * <p>In client mode the operation documents are read too ({@link OperationInput}), assembled and validated against the schema, and
 * lowered into the operations, the fragments and the result models of the IR.</p>
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
        return fromDirectory(directory, options, List.of());
    }

    /**
     * Builds the IR of the schema files under a directory, with the type mappings of dependencies.
     *
     * @param directory    the schema root; the origins in the IR are relative to it
     * @param options      the options
     * @param dependencies the jars and class directories to read {@value TypeMappingsFile#PATH} from, used when the option
     *                     {@code typeMappingsFromDependencies} is on
     * @return the IR and the diagnostics
     */
    public static IrResult fromDirectory(Path directory, Options options, Collection<Path> dependencies) {
        return fromDirectory(directory, options, dependencies, OperationInput.directory(directory));
    }

    /**
     * Builds the IR of the schema files under a directory, with the type mappings of dependencies and the operation documents of
     * client mode.
     *
     * @param directory    the schema root; the origins in the IR are relative to it
     * @param options      the options
     * @param dependencies the jars and class directories to read {@value TypeMappingsFile#PATH} from
     * @param operations   the operation documents; server mode ignores them. The shorter forms read them from the schema directory.
     * @return the IR and the diagnostics
     */
    public static IrResult fromDirectory(Path directory, Options options, Collection<Path> dependencies, OperationInput operations) {
        Objects.requireNonNull(directory, "directory");
        return build(options, dependencies, diagnostics -> SchemaFiles.readDirectory(directory, options.includes(), options.excludes(), diagnostics),
                operations);
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
        return fromFiles(files, options, List.of());
    }

    /**
     * Builds the IR of schema files, with the type mappings of dependencies.
     *
     * @param files        the files, in any order
     * @param options      the options
     * @param dependencies the jars and class directories to read {@value TypeMappingsFile#PATH} from, used when the option
     *                     {@code typeMappingsFromDependencies} is on
     * @return the IR and the diagnostics
     */
    public static IrResult fromFiles(Collection<Path> files, Options options, Collection<Path> dependencies) {
        return fromFiles(files, options, dependencies, OperationInput.none());
    }

    /**
     * Builds the IR of schema files, with the type mappings of dependencies and the operation documents of client mode.
     *
     * @param files        the files, in any order
     * @param options      the options
     * @param dependencies the jars and class directories to read {@value TypeMappingsFile#PATH} from
     * @param operations   the operation documents; server mode ignores them
     * @return the IR and the diagnostics
     */
    public static IrResult fromFiles(Collection<Path> files, Options options, Collection<Path> dependencies, OperationInput operations) {
        Objects.requireNonNull(files, "files");
        return build(options, dependencies, diagnostics -> SchemaFiles.readFiles(files, diagnostics), operations);
    }

    /**
     * Builds the IR of schema text that the caller has read.
     *
     * @param sources the sources, in any order
     * @param options the options
     * @return the IR and the diagnostics
     */
    public static IrResult fromSources(Collection<SchemaSource> sources, Options options) {
        return fromSources(sources, options, List.of());
    }

    /**
     * Builds the IR of schema text that the caller has read, with the type mappings of dependencies.
     *
     * @param sources      the sources, in any order
     * @param options      the options
     * @param dependencies the jars and class directories to read {@value TypeMappingsFile#PATH} from, used when the option
     *                     {@code typeMappingsFromDependencies} is on
     * @return the IR and the diagnostics
     */
    public static IrResult fromSources(Collection<SchemaSource> sources, Options options, Collection<Path> dependencies) {
        return fromSources(sources, options, dependencies, OperationInput.none());
    }

    /**
     * Builds the IR of schema text that the caller has read, with the type mappings of dependencies and the operation documents of
     * client mode.
     *
     * @param sources      the sources, in any order
     * @param options      the options
     * @param dependencies the jars and class directories to read {@value TypeMappingsFile#PATH} from
     * @param operations   the operation documents; server mode ignores them
     * @return the IR and the diagnostics
     */
    public static IrResult fromSources(Collection<SchemaSource> sources, Options options, Collection<Path> dependencies,
                                       OperationInput operations) {
        Objects.requireNonNull(sources, "sources");
        return build(options, dependencies, diagnostics -> List.copyOf(sources), operations);
    }

    private static IrResult build(Options options, Collection<Path> dependencies, Function<List<Diagnostic>, List<SchemaSource>> discovery,
                                  OperationInput operations) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(operations, "operations");
        List<Diagnostic> diagnostics = new ArrayList<>(options.validate());
        List<SchemaSource> sources = discovery.apply(diagnostics);
        SchemaLoader.Loaded loaded = SchemaLoader.load(sources, diagnostics);
        Map<String, String> inherited = options.typeMappingsFromDependencies()
                ? TypeMappingsFile.read(dependencies, diagnostics) : Map.of();
        Ir ir = Ir.EMPTY;
        boolean schemaUsable = diagnostics.stream().noneMatch(Diagnostic::isError);
        OperationDocuments.Parsed documents = null;
        if (options.mode() == Mode.CLIENT && schemaUsable) {
            // a problem of one document does not stop the check of the others: the operations that are well formed are still lowered
            documents = OperationDocuments.parse(operations.read(options, diagnostics), diagnostics);
        }
        if (schemaUsable) {
            ir = Lowering.lower(options, loaded.schema(), inherited, documents, diagnostics);
            if (diagnostics.stream().anyMatch(Diagnostic::isError)) {
                ir = Ir.EMPTY;
            }
        }
        return new IrResult(ir, diagnostics, loaded.skipped());
    }
}
