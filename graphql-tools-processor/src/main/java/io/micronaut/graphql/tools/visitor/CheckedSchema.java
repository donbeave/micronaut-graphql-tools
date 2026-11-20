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
package io.micronaut.graphql.tools.visitor;

import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSourceException;
import io.micronaut.graphql.tools.binding.internal.schema.TypeExtensions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The schema of one compilation. It is read on the first request, once, and only when a class is checked: a compilation
 * with no resolver class reads no file. Whatever fails to load is kept as findings, so that the compilation reports it
 * once and checks nothing against a schema it could not read. The instance belongs to one visitor start and is never
 * shared.
 */
final class CheckedSchema {

    private final List<Path> directories;
    private boolean loaded;
    @Nullable
    private SchemaModel model;
    private List<Finding> problems = List.of();
    private int files;
    private int loads;

    CheckedSchema(List<Path> directories) {
        this.directories = List.copyOf(directories);
    }

    /**
     * @return the schema, or empty when it cannot be read; {@link #problems()} says why
     */
    Optional<SchemaModel> model() {
        if (!loaded) {
            loaded = true;
            loads++;
            load();
        }
        return Optional.ofNullable(model);
    }

    /**
     * @return the findings of a schema that could not be read, empty when it was read or has not been requested
     */
    List<Finding> problems() {
        return problems;
    }

    /**
     * @return how often the schema was read: at most once
     */
    int loads() {
        return loads;
    }

    /**
     * @return the number of schema files that were read
     */
    int files() {
        return files;
    }

    /**
     * @return the directories, as the compilation was given them
     */
    List<Path> directories() {
        return directories;
    }

    /**
     * @return the findings for the directories that do not exist; checked at the start, before any class
     */
    List<Finding> missingDirectories() {
        List<Finding> result = new ArrayList<>();
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) {
                result.add(new Finding(BindingCode.PROCESSOR_SCHEMA_NOT_FOUND.code(), null,
                        "the schema directory " + directory.toAbsolutePath() + " does not exist or is not a directory",
                        null, null, null, "correct " + ProcessorOptions.SCHEMA_DIRS));
            }
        }
        return result;
    }

    private void load() {
        try {
            List<SchemaSources.Source> sources = SchemaSources.readDirectories(directories).stream()
                    .map(it -> new SchemaSources.Source(absolute(it.name()), it.text()))
                    .toList();
            if (sources.isEmpty()) {
                problem(BindingCode.PROCESSOR_SCHEMA_NOT_FOUND, "no *" + SchemaSources.EXTENSION + " file in " + directories
                        + "; the runtime reads the same files, and a client operation file such as *.graphql is not a schema file",
                        "correct " + ProcessorOptions.SCHEMA_DIRS);
                return;
            }
            SchemaSources.Loaded loaded = SchemaSources.load(sources);
            files = loaded.read().size();
            if (loaded.read().isEmpty()) {
                problem(BindingCode.PROCESSOR_SCHEMA_NOT_FOUND, "no schema in " + directories + ": every file is empty or "
                        + "holds only operations, skipped: " + loaded.skipped(), "correct " + ProcessorOptions.SCHEMA_DIRS);
                return;
            }
            SchemaSources.addImplicitRoots(loaded.registry());
            TypeDefinitionRegistry merged = TypeExtensions.merge(loaded.registry());
            model = SchemaModel.load(merged).orElse(null);
            if (model == null) {
                problem(BindingCode.SCHEMA_DEFINITION_NOT_PROVIDED, "the schema in " + directories + " has no schema definition "
                        + "and no object type named Query: define a Query type or a schema block", null);
            }
        } catch (SchemaSourceException e) {
            problem(BindingCode.SCHEMA_INVALID, e.getMessage(), null);
        }
    }

    // a source is named by its path relative to its directory; the diagnostics name the file by its absolute path
    private String absolute(String name) {
        if (directories.size() > 1) {
            return Path.of(name).toAbsolutePath().toString();
        }
        return directories.get(0).resolve(name).toAbsolutePath().toString();
    }

    private void problem(BindingCode code, String sentence, @Nullable String fix) {
        problems = List.of(new Finding(code.code(), null, sentence, null, null, null, fix));
    }
}
