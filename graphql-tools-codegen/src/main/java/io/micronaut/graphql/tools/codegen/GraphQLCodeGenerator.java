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

import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * <p>The generator checks the options and reads each schema file with the graphql-java parser. Every problem is a
 * {@link Diagnostic} in the result; nothing is thrown for the content of an option or a schema. The generator is a plain object
 * with no Gradle, Maven or Micronaut types, so a build plugin maps its configuration onto the builder and {@link Options}.</p>
 *
 * <p>This version produces no files yet: the schema model and the emitters arrive in later changes, and the result lists no
 * file until they do.</p>
 */
public final class GraphQLCodeGenerator {

    private final List<Path> schemaFiles;
    private final Path outputDirectory;
    private final Options options;

    private GraphQLCodeGenerator(Builder builder) {
        this.schemaFiles = List.copyOf(builder.schemaFiles);
        this.outputDirectory = builder.outputDirectory;
        this.options = builder.options;
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
        List<Diagnostic> diagnostics = new ArrayList<>(options.validate());
        for (Path schemaFile : schemaFiles) {
            check(schemaFile, diagnostics);
        }
        return new GenerationResult(List.of(), diagnostics);
    }

    private static void check(Path schemaFile, List<Diagnostic> diagnostics) {
        String path = schemaFile.toString().replace('\\', '/');
        String text;
        try {
            text = Files.readString(schemaFile, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.SCHEMA_FILE_UNREADABLE,
                    "The schema file " + path + " cannot be read: " + e.getMessage(), null, new Origin(path, 0, 0)));
            return;
        }
        try {
            new Parser().parseDocument(text);
        } catch (InvalidSyntaxException e) {
            int line = e.getLocation() != null ? e.getLocation().getLine() : 0;
            int column = e.getLocation() != null ? e.getLocation().getColumn() : 0;
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.SCHEMA_SYNTAX_ERROR,
                    "The schema file " + path + " is not valid GraphQL: " + e.getMessage(), null, new Origin(path, line, column)));
        }
    }

    /**
     * A builder of {@link GraphQLCodeGenerator}.
     */
    public static final class Builder {

        private final List<Path> schemaFiles = new ArrayList<>();
        private Path outputDirectory;
        private Options options;

        Builder() {
        }

        /**
         * Adds schema files.
         *
         * @param files the files, in the order to read them
         * @return this builder
         */
        public Builder withSchemaFiles(Path... files) {
            return withSchemaFiles(List.of(files));
        }

        /**
         * Adds schema files.
         *
         * @param files the files, in the order to read them
         * @return this builder
         */
        public Builder withSchemaFiles(Collection<Path> files) {
            files.forEach(file -> schemaFiles.add(Objects.requireNonNull(file, "schema file")));
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
         * @throws IllegalStateException when there is no schema file, no output directory or no options: the caller wired the
         *                               generator wrongly, which is not a problem of an option value
         */
        public GraphQLCodeGenerator build() {
            if (schemaFiles.isEmpty()) {
                throw new IllegalStateException("withSchemaFiles was not called with a schema file");
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
