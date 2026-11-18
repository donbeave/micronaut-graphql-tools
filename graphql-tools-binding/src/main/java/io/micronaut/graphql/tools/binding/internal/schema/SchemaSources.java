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
package io.micronaut.graphql.tools.binding.internal.schema;

import graphql.GraphQLError;
import graphql.language.Definition;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.language.Document;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.TypeName;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.MultiSourceReader;
import graphql.parser.Parser;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.SchemaProblem;
import io.micronaut.core.annotation.Internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns schema sources, files or classpath resources that the caller has already read, into one registry. The runtime and
 * the compile-time checks both use it, so that they apply the same rules: a file that holds only executable definitions
 * (client operations) is skipped and reported, every other file is parsed with its name as the source name, and the
 * {@code Query}, {@code Mutation} and {@code Subscription} types are the root operation types of a schema without a
 * {@code schema} block.
 */
@Internal
public final class SchemaSources {

    /**
     * The extension of the schema files that are read by default.
     */
    public static final String EXTENSION = ".graphqls";

    private static final Map<String, String> IMPLICIT_ROOTS = Map.of(
            "query", "Query", "mutation", "Mutation", "subscription", "Subscription");

    private SchemaSources() {
    }

    /**
     * Parses and merges sources.
     *
     * @param sources the sources, in merge order
     * @return the registry and the names read and skipped
     * @throws SchemaSourceException when a source does not parse or the sources conflict
     */
    public static Loaded load(List<Source> sources) {
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry();
        SchemaParser parser = new SchemaParser();
        List<String> read = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Source source : sources) {
            if (source.text().isBlank()) {
                skipped.add(source.name());
                continue;
            }
            Document document = parse(source);
            if (executableOnly(document)) {
                skipped.add(source.name());
                continue;
            }
            TypeDefinitionRegistry parsed;
            try {
                parsed = parser.buildRegistry(document);
            } catch (SchemaProblem e) {
                throw new SchemaSourceException("the schema file " + source.name() + " is not valid: " + describe(e), e);
            }
            try {
                registry.merge(parsed);
            } catch (SchemaProblem e) {
                throw new SchemaSourceException("the schema file " + source.name() + " conflicts with " + read + ": " + describe(e), e);
            }
            read.add(source.name());
        }
        return new Loaded(registry, List.copyOf(read), List.copyOf(skipped));
    }

    /**
     * Reads the {@value #EXTENSION} files of directories, recursively, in sorted path order. A source is named by its
     * path relative to its directory, and by that path behind the directory when there is more than one directory.
     *
     * @param directories the directories
     * @return the sources
     * @throws SchemaSourceException when a directory does not exist or a file cannot be read
     */
    public static List<Source> readDirectories(List<Path> directories) {
        List<Source> sources = new ArrayList<>();
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) {
                throw new SchemaSourceException("not a directory: " + directory.toAbsolutePath());
            }
            List<Path> files;
            try (Stream<Path> paths = Files.walk(directory)) {
                files = paths.filter(Files::isRegularFile)
                        .filter(it -> it.getFileName().toString().endsWith(EXTENSION))
                        .sorted()
                        .collect(Collectors.toList());
            } catch (IOException | UncheckedIOException e) {
                throw new SchemaSourceException("cannot list the schema files in " + directory.toAbsolutePath() + ": "
                        + e.getMessage(), e);
            }
            for (Path file : files) {
                String relative = directory.relativize(file).toString().replace('\\', '/');
                try {
                    sources.add(new Source(directories.size() > 1 ? directory + "/" + relative : relative,
                            Files.readString(file, StandardCharsets.UTF_8)));
                } catch (IOException e) {
                    throw new SchemaSourceException("cannot read the schema file " + file.toAbsolutePath() + ": "
                            + e.getMessage(), e);
                }
            }
        }
        return sources;
    }

    /**
     * The root operation types of a schema that has no {@code schema} block: the object types named {@code Query},
     * {@code Mutation} and {@code Subscription}.
     *
     * @param registry the schema
     * @return the root type names keyed by operation, empty when the schema has a {@code schema} block
     */
    public static Map<String, String> implicitRoots(TypeDefinitionRegistry registry) {
        Map<String, String> roots = new LinkedHashMap<>();
        if (registry.schemaDefinition().isPresent()) {
            return roots;
        }
        IMPLICIT_ROOTS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (registry.getType(entry.getValue()).filter(ObjectTypeDefinition.class::isInstance).isPresent()) {
                roots.put(entry.getKey(), entry.getValue());
            }
        });
        return roots;
    }

    /**
     * Gives a schema without a {@code schema} block the implicit root operation types, in place.
     *
     * @param registry the schema
     */
    public static void addImplicitRoots(TypeDefinitionRegistry registry) {
        Map<String, String> roots = implicitRoots(registry);
        if (roots.isEmpty()) {
            return;
        }
        SchemaDefinition.Builder schema = SchemaDefinition.newSchemaDefinition();
        for (String operation : List.of("query", "mutation", "subscription")) {
            String type = roots.get(operation);
            if (type != null) {
                schema.operationTypeDefinition(OperationTypeDefinition.newOperationTypeDefinition()
                        .name(operation).typeName(TypeName.newTypeName(type).build()).build());
            }
        }
        registry.add(schema.build());
    }

    private static Document parse(Source source) {
        try {
            return new Parser().parseDocument(MultiSourceReader.newMultiSourceReader().string(source.text(), source.name()).build());
        } catch (InvalidSyntaxException e) {
            throw new SchemaSourceException("the schema file " + source.name() + " is not valid: " + e.getMessage(), e);
        }
    }

    private static boolean executableOnly(Document document) {
        List<Definition> definitions = document.getDefinitions();
        return !definitions.isEmpty() && definitions.stream()
                .allMatch(it -> it instanceof OperationDefinition || it instanceof FragmentDefinition);
    }

    private static String describe(SchemaProblem problem) {
        return problem.getErrors().stream()
                .map(GraphQLError::getMessage)
                .collect(Collectors.joining("; "));
    }

    /**
     * One schema file.
     *
     * @param name the name that locations in messages use
     * @param text the content
     */
    public record Source(String name, String text) {
    }

    /**
     * What was loaded.
     *
     * @param registry the merged schema
     * @param read     the names of the sources that hold schema definitions, in the order they were merged
     * @param skipped  the names of the sources that hold only executable definitions or nothing
     */
    public record Loaded(TypeDefinitionRegistry registry, List<String> read, List<String> skipped) {
    }
}
