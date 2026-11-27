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
 * the directory {@value #DEFAULT_SCHEMA_DIRECTORY} relative to the working directory is read. The only file that
 * this version produces is {@value TypeMappingsFile#PATH}, which maps the types of the module to their classes; the emitters
 * arrive in later changes.</p>
 */
public final class GraphQLCodeGenerator {

    /** The schema directory that is read when the builder is given neither files nor a directory. */
    public static final String DEFAULT_SCHEMA_DIRECTORY = "src/main/resources/graphql";

    private final List<Path> schemaFiles;
    private final Path schemaDirectory;
    private final Path outputDirectory;
    private final Options options;
    private final List<Path> dependencies;

    private GraphQLCodeGenerator(Builder builder) {
        this.schemaFiles = List.copyOf(builder.schemaFiles);
        this.schemaDirectory = builder.schemaDirectory != null || !builder.schemaFiles.isEmpty() ? builder.schemaDirectory
                : Path.of(DEFAULT_SCHEMA_DIRECTORY);
        this.outputDirectory = builder.outputDirectory;
        this.options = builder.options;
        this.dependencies = List.copyOf(builder.dependencies);
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
     * @return the files and the diagnostics; there is no file when a diagnostic is an error
     */
    public GenerationResult generate() {
        IrResult result = toIr();
        List<GeneratedFile> files = new ArrayList<>();
        if (!result.hasErrors()) {
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
        return schemaDirectory != null ? IrBuilder.fromDirectory(schemaDirectory, options, dependencies)
                : IrBuilder.fromFiles(schemaFiles, options, dependencies);
    }

    /**
     * A builder of {@link GraphQLCodeGenerator}.
     */
    public static final class Builder {

        private final List<Path> schemaFiles = new ArrayList<>();
        private Path schemaDirectory;
        private final List<Path> dependencies = new ArrayList<>();
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
         * @throws IllegalStateException when there are both schema files and a schema directory, or no output directory or no
         *                               options: the caller wired the generator wrongly, which is not a problem of an option value
         */
        public GraphQLCodeGenerator build() {
            if (!schemaFiles.isEmpty() && schemaDirectory != null) {
                throw new IllegalStateException("withSchemaFiles and withSchemaDirectory are alternatives: call only one");
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
