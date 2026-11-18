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
package io.micronaut.graphql.tools.processing;

import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSourceException;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the schema directories of the compilation with the rules of the runtime ({@link SchemaSources}): the
 * {@value SchemaSources#EXTENSION} files, files without type definitions skipped with a note, and the implicit root types.
 */
final class SchemaFiles {

    private SchemaFiles() {
    }

    static TypeDefinitionRegistry read(String sdlDir, Problems problems) {
        List<Path> directories = new ArrayList<>();
        for (String entry : sdlDir.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            Path directory = Path.of(entry.trim());
            if (!Files.isDirectory(directory)) {
                problems.error(ProcessorOptions.SDL_DIR + " is not a directory: " + directory.toAbsolutePath() + ".");
                return null;
            }
            directories.add(directory);
        }
        try {
            List<SchemaSources.Source> sources = SchemaSources.readDirectories(directories);
            if (sources.isEmpty()) {
                problems.error(ProcessorOptions.SDL_DIR + " has no *" + SchemaSources.EXTENSION + " file: " + sdlDir
                        + ". The runtime reads the same files by default; a client operation file such as "
                        + "*.graphql is not a schema file.");
                return null;
            }
            SchemaSources.Loaded loaded = SchemaSources.load(sources);
            if (!loaded.skipped().isEmpty()) {
                problems.info("skipped " + loaded.skipped() + " in " + sdlDir + ": only executable definitions or nothing.");
            }
            SchemaSources.addImplicitRoots(loaded.registry());
            return loaded.registry();
        } catch (SchemaSourceException e) {
            problems.error(e.getMessage());
            return null;
        }
    }

}
