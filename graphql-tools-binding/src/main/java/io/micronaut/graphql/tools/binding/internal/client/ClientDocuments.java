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
package io.micronaut.graphql.tools.binding.internal.client;

import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.language.SourceLocation;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.MultiSourceReader;
import graphql.parser.Parser;
import graphql.parser.ParserEnvironment;
import graphql.parser.ParserOptions;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.DocumentLocation;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Parsing of client documents and the index of the {@code *.graphql} files of a schema directory: the operations by name and
 * the fragments by name, each with the file it came from (the source name of its nodes).
 */
@Internal
public final class ClientDocuments {

    /** The extension of the files that hold operations and fragments. */
    public static final String EXTENSION = ".graphql";

    // a document is at most the size of the annotation metadata string, which holds some 65,000 characters; the default limit
    // of 15,000 tokens would reject a document that fits, with a message that does not say why
    private static final ParserOptions OPTIONS = ParserOptions.newParserOptions().maxTokens(100_000).build();

    private ClientDocuments() {
    }

    /**
     * @param text   the document
     * @param source the name that the locations of its nodes carry (the path of the file), null for the document of an annotation
     * @return the document, or what is wrong with it
     */
    public static Parsed parse(String text, @Nullable String source) {
        try {
            Document document = new Parser().parseDocument(ParserEnvironment.newParserEnvironment()
                    .document(MultiSourceReader.newMultiSourceReader().string(text, source).build())
                    .parserOptions(OPTIONS)
                    .build());
            return new Parsed(document, null, null);
        } catch (InvalidSyntaxException e) {
            SourceLocation at = e.getLocation();
            return new Parsed(null, e.getMessage(), at == null ? new DocumentLocation(source, 1, 1)
                    : new DocumentLocation(source, at.getLine(), at.getColumn()));
        }
    }

    /**
     * Reads the {@value #EXTENSION} files of directories, recursively, in sorted path order, and indexes them.
     *
     * @param directories the directories
     * @return the index
     */
    public static Index index(List<Path> directories) {
        Map<String, List<OperationDefinition>> operations = new LinkedHashMap<>();
        Map<String, List<FragmentDefinition>> fragments = new LinkedHashMap<>();
        List<Finding> problems = new ArrayList<>();
        for (Path file : files(directories, problems)) {
            String path = file.toAbsolutePath().toString();
            String text;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                problems.add(invalid(null, "cannot read the document file " + path + ": " + e.getMessage(), null));
                continue;
            }
            Parsed parsed = parse(text, path);
            if (parsed.document() == null) {
                problems.add(invalid(null, "the document file " + path + " is not valid: " + parsed.message(), parsed.location()));
                continue;
            }
            for (OperationDefinition operation : parsed.document().getDefinitionsOfType(OperationDefinition.class)) {
                if (operation.getName() != null) {
                    operations.computeIfAbsent(operation.getName(), key -> new ArrayList<>()).add(operation);
                }
            }
            for (FragmentDefinition fragment : parsed.document().getDefinitionsOfType(FragmentDefinition.class)) {
                fragments.computeIfAbsent(fragment.getName(), key -> new ArrayList<>()).add(fragment);
            }
        }
        fragments.forEach((name, definitions) -> {
            if (definitions.size() > 1) {
                problems.add(invalid(null, "the fragment " + name + " is defined " + definitions.size() + " times in the document "
                        + "files, in " + definitions.stream().map(it -> String.valueOf(it.getSourceLocation().getSourceName())).toList(),
                        location(definitions.get(1).getSourceLocation())));
            }
        });
        return new Index(operations, fragments, problems);
    }

    /**
     * @param coordinate the coordinate of the finding, null for none
     * @param sentence   what is wrong
     * @param location   where, null for nowhere
     * @return a {@code client-document-invalid} finding
     */
    static Finding invalid(@Nullable String coordinate, String sentence, @Nullable DocumentLocation location) {
        return new Finding(BindingCode.CLIENT_DOCUMENT_INVALID.getCode(), coordinate, sentence, null, null, null,
                "correct the document" + (location == null ? "" : " at the position that the message gives"), location);
    }

    /**
     * @param at a source location of a node
     * @return the location, with the source name of the node as its path
     */
    static @Nullable DocumentLocation location(@Nullable SourceLocation at) {
        return at == null ? null : new DocumentLocation(at.getSourceName(), at.getLine(), at.getColumn());
    }

    private static List<Path> files(List<Path> directories, List<Finding> problems) {
        List<Path> files = new ArrayList<>();
        for (Path directory : directories) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.filter(Files::isRegularFile)
                        .filter(it -> it.getFileName().toString().endsWith(EXTENSION))
                        .sorted()
                        .forEach(files::add);
            } catch (IOException | UncheckedIOException e) {
                problems.add(invalid(null, "cannot list the document files in " + directory.toAbsolutePath() + ": " + e.getMessage(), null));
            }
        }
        return files;
    }

    /**
     * The result of parsing one document.
     *
     * @param document the document, null when it does not parse
     * @param message  what is wrong, null when it parses
     * @param location where it is wrong, null when it parses
     */
    public record Parsed(@Nullable Document document, @Nullable String message, @Nullable DocumentLocation location) {
    }

    /**
     * The operations and fragments of the documents of one schema directory.
     *
     * @param operations the operations that have a name, by name, in file order
     * @param fragments  the fragments, by name, in file order
     * @param problems   the files that do not parse and the fragments that are defined twice
     */
    public record Index(Map<String, List<OperationDefinition>> operations, Map<String, List<FragmentDefinition>> fragments,
                        List<Finding> problems) {

        public Index {
            operations = Map.copyOf(operations);
            fragments = Map.copyOf(fragments);
            problems = List.copyOf(problems);
        }
    }
}
