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

import io.micronaut.graphql.tools.codegen.schema.IrBuilder;
import io.micronaut.graphql.tools.codegen.schema.IrResult;
import io.micronaut.graphql.tools.codegen.schema.OperationInput;
import io.micronaut.graphql.tools.codegen.schema.SchemaSource;
import io.micronaut.graphql.tools.codegen.schema.TypeMappingsFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The entry point of the code generator:
 *
 * <pre>{@code
 * GenerationResult result = GraphQLCodeGenerator.builder()
 *         .withSchemaFiles(schemaFile)
 *         .withOutputDirectory(outputDirectory)
 *         .withOptions(options)
 *         .build()
 *         .generate();
 * }</pre>
 *
 * <p>The generator checks the options, loads the schema files and lowers them into the intermediate representation ({@link
 * #toIr()}). Every problem is a {@link Diagnostic} in the result; nothing is thrown for the content of an option or a schema.
 * The generator is a plain object with no Gradle, Maven or Micronaut types, so a build plugin maps its configuration onto the
 * builder and {@link Options}.</p>
 *
 * <p>The schema files are given as files ({@code withSchemaFiles}) or as a directory ({@code withSchemaDirectory}); with neither,
 * the directory {@value #DEFAULT_SCHEMA_DIRECTORY} relative to the working directory is read. For the Java language the enums are
 * emitted; the other types and the other languages list no source file until their emitters arrive. The type mappings file
 * {@value TypeMappingsFile#PATH} maps the types of the module to their classes.</p>
 *
 * <p>In client mode the generator also reads operation documents: the files of a directory ({@code withOperationDirectory}, selected
 * by the options {@code operationIncludes} and {@code operationExcludes}), files ({@code withOperationFiles}) or text
 * ({@code withOperationSources}). Without any of them it reads the {@code *.graphql} files of the schema directory, where the
 * compile-time checks look for them. The operations, their fragments and the result models they select are in the IR.</p>
 */
public final class GraphQLCodeGenerator {

    /** The schema directory that is read when the builder is given neither files nor a directory. */
    public static final String DEFAULT_SCHEMA_DIRECTORY = "src/main/resources/graphql";

    private final List<Path> schemaFiles;
    private final Path schemaDirectory;
    private final Path outputDirectory;
    private final Options options;
    private final List<Path> dependencies;
    private final OperationInput operations;

    private GraphQLCodeGenerator(Builder builder) {
        this.schemaFiles = List.copyOf(builder.schemaFiles);
        this.schemaDirectory = builder.schemaDirectory != null || !builder.schemaFiles.isEmpty() ? builder.schemaDirectory
                : Path.of(DEFAULT_SCHEMA_DIRECTORY);
        this.outputDirectory = builder.outputDirectory;
        this.options = builder.options;
        this.dependencies = List.copyOf(builder.dependencies);
        this.operations = builder.operationInput();
    }

    /**
     * Starts a generator.
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The output directory of this generator.
     *
     * @return the directory that the generated files are relative to
     */
    public Path getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Checks the options and the schema files, and generates the files.
     *
     * @return the files and the diagnostics; there is no file when a diagnostic is an error, and none for a language whose emitter
     *         does not exist yet
     */
    public GenerationResult generate() {
        IrResult result = toIr();
        List<GeneratedFile> files = new ArrayList<>();
        if (!result.hasErrors()) {
            if (options.language() == Language.JAVA) {
                files.addAll(new JavaEmitter().emit(result.ir(), options));
            }
            GeneratedFile mappings = TypeMappingsFile.of(result.ir());
            if (mappings != null) {
                files.add(mappings);
            }
        }
        return new GenerationResult(files, result.diagnostics());
    }

    /**
     * Checks the options, loads the schema files and builds the intermediate representation. The result holds the IR, which is
     * empty when a diagnostic is an error, the diagnostics, and the schema files that were skipped.
     *
     * @return the IR and the diagnostics
     */
    public IrResult toIr() {
        OperationInput input = operations != null ? operations
                : schemaDirectory != null ? OperationInput.directory(schemaDirectory) : OperationInput.none();
        return schemaDirectory != null ? IrBuilder.fromDirectory(schemaDirectory, options, dependencies, input)
                : IrBuilder.fromFiles(schemaFiles, options, dependencies, input);
    }

    /**
     * A builder of {@link GraphQLCodeGenerator}.
     */
    public static final class Builder {

        private final List<Path> schemaFiles = new ArrayList<>();
        private Path schemaDirectory;
        private final List<Path> dependencies = new ArrayList<>();
        private final List<Path> operationFiles = new ArrayList<>();
        private final List<SchemaSource> operationSources = new ArrayList<>();
        private Path operationDirectory;
        private Path outputDirectory;
        private Options options;

        Builder() {
        }

        /**
         * Adds schema files.
         *
         * @param files the files, in any order: they are sorted by path before they are read
         * @return this builder
         */
        public Builder withSchemaFiles(Path... files) {
            return withSchemaFiles(List.of(files));
        }

        /**
         * Adds schema files.
         *
         * @param files the files, in any order: they are sorted by path before they are read
         * @return this builder
         */
        public Builder withSchemaFiles(Collection<Path> files) {
            files.forEach(file -> schemaFiles.add(Objects.requireNonNull(file, "schema file")));
            return this;
        }

        /**
         * Sets the directory that holds the schema files. The files are those whose path relative to the directory matches the
         * options {@code includes} and not {@code excludes}, and the origins of the diagnostics are relative to it.
         *
         * @param directory the schema root
         * @return this builder
         */
        public Builder withSchemaDirectory(Path directory) {
            this.schemaDirectory = Objects.requireNonNull(directory, "schemaDirectory");
            return this;
        }

        /**
         * Adds operation documents of client mode as files. The paths in the IR and in the diagnostics are relative to the closest
         * directory that holds all of them; the files are read in the order of those paths.
         *
         * @param files the files, in any order: they are sorted by path before they are read
         * @return this builder
         */
        public Builder withOperationFiles(Path... files) {
            return withOperationFiles(List.of(files));
        }

        /**
         * Adds operation documents of client mode as files. The paths in the IR and in the diagnostics are relative to the closest
         * directory that holds all of them; the files are read in the order of those paths.
         *
         * @param files the files, in any order: they are sorted by path before they are read
         * @return this builder
         */
        public Builder withOperationFiles(Collection<Path> files) {
            files.forEach(file -> operationFiles.add(Objects.requireNonNull(file, "operation file")));
            return this;
        }

        /**
         * Sets the directory that holds the operation documents of client mode. The files are those whose path relative to the
         * directory matches the options {@code operationIncludes} and not {@code operationExcludes}, in the order of those paths.
         *
         * @param directory the directory
         * @return this builder
         */
        public Builder withOperationDirectory(Path directory) {
            this.operationDirectory = Objects.requireNonNull(directory, "operationDirectory");
            return this;
        }

        /**
         * Adds operation documents of client mode that the caller has read.
         *
         * @param sources the documents, in any order: the path of a source is its origin and they are read in the order of the paths
         * @return this builder
         */
        public Builder withOperationSources(Collection<SchemaSource> sources) {
            sources.forEach(source -> operationSources.add(Objects.requireNonNull(source, "operation source")));
            return this;
        }

        OperationInput operationInput() {
            if (operationDirectory != null) {
                return OperationInput.directory(operationDirectory);
            } else if (!operationFiles.isEmpty()) {
                return OperationInput.files(operationFiles);
            } else if (!operationSources.isEmpty()) {
                return OperationInput.sources(operationSources);
            }
            return null;
        }

        /**
         * Adds dependency jars or class directories. When the option {@code typeMappingsFromDependencies} is on, the generator reads
         * {@value TypeMappingsFile#PATH} from each of them and applies the mappings below {@code typeMappings}.
         *
         * @param paths the jars and directories, in any order: they are sorted by path before they are read
         * @return this builder
         */
        public Builder withDependencies(Collection<Path> paths) {
            paths.forEach(path -> dependencies.add(Objects.requireNonNull(path, "dependency")));
            return this;
        }

        /**
         * Sets the directory that the generated files are written under.
         *
         * @param directory the directory
         * @return this builder
         */
        public Builder withOutputDirectory(Path directory) {
            this.outputDirectory = Objects.requireNonNull(directory, "outputDirectory");
            return this;
        }

        /**
         * Sets the options.
         *
         * @param value the options
         * @return this builder
         */
        public Builder withOptions(Options value) {
            this.options = Objects.requireNonNull(value, "options");
            return this;
        }

        /**
         * Builds the generator.
         *
         * @return the generator
         * @throws IllegalStateException when there are both schema files and a schema directory, or more than one of operation files,
         *                               an operation directory and operation sources, or no output directory or no options: the
         *                               caller wired the generator wrongly, which is not a problem of an option value
         */
        public GraphQLCodeGenerator build() {
            if (!schemaFiles.isEmpty() && schemaDirectory != null) {
                throw new IllegalStateException("withSchemaFiles and withSchemaDirectory are alternatives: call only one");
            }
            int kinds = (operationDirectory != null ? 1 : 0) + (operationFiles.isEmpty() ? 0 : 1) + (operationSources.isEmpty() ? 0 : 1);
            if (kinds > 1) {
                throw new IllegalStateException("withOperationDirectory, withOperationFiles and withOperationSources are alternatives:"
                        + " call only one");
            }
            if (outputDirectory == null) {
                throw new IllegalStateException("withOutputDirectory was not called");
            }
            if (options == null) {
                throw new IllegalStateException("withOptions was not called");
            }
            return new GraphQLCodeGenerator(this);
        }
    }
}
