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

import graphql.GraphQLError;
import graphql.parser.MultiSourceReader;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.SchemaProblem;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the schema files of {@link ProcessorOptions#SDL_DIR} with graphql-java's parser: every {@code *.graphqls} and
 * {@code *.graphql} file under the directory, in the order of their paths, merged into one registry. Each schema
 * element keeps the path of its file, relative to the directory, as its source name.
 */
final class SchemaFiles {

    private SchemaFiles() {
    }

    /**
     * @param sdlDir   the directory
     * @param problems where a missing directory, an unreadable file or a parse error is reported
     * @return the registry, or {@code null} after an error
     */
    static TypeDefinitionRegistry read(String sdlDir, Problems problems) {
        Path dir = Path.of(sdlDir);
        if (!Files.isDirectory(dir)) {
            problems.error(ProcessorOptions.SDL_DIR + " is not a directory: " + dir.toAbsolutePath() + ".");
            return null;
        }

        List<Path> files;
        try (Stream<Path> paths = Files.walk(dir)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(it -> it.toString().endsWith(".graphqls") || it.toString().endsWith(".graphql"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            problems.error("cannot list the schema files in " + dir.toAbsolutePath() + ": " + e.getMessage());
            return null;
        }
        if (files.isEmpty()) {
            problems.error(ProcessorOptions.SDL_DIR + " has no *.graphqls or *.graphql file: " + dir.toAbsolutePath()
                    + ".");
            return null;
        }

        TypeDefinitionRegistry registry = new TypeDefinitionRegistry();
        SchemaParser parser = new SchemaParser();
        for (Path file : files) {
            String sourceName = dir.relativize(file).toString().replace('\\', '/');
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                MultiSourceReader source = MultiSourceReader.newMultiSourceReader().reader(reader, sourceName).build();
                registry.merge(parser.parse(source));
            } catch (IOException e) {
                problems.error("cannot read the schema file " + file.toAbsolutePath() + ": " + e.getMessage());
                return null;
            } catch (SchemaProblem e) {
                problems.error("the schema file " + sourceName + " is not valid: " + describe(e.getErrors()));
                return null;
            }
        }
        return registry;
    }

    private static String describe(List<GraphQLError> errors) {
        return errors.stream()
                .map(it -> it.getMessage() + (it.getLocations() == null || it.getLocations().isEmpty() ? ""
                        : " (line " + it.getLocations().get(0).getLine() + ")"))
                .collect(Collectors.joining("; "));
    }

}
