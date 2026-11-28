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

import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.MultiSourceReader;
import graphql.parser.Parser;
import graphql.parser.ParserEnvironment;
import graphql.parser.ParserOptions;
import io.micronaut.graphql.tools.codegen.Diagnostic;
import io.micronaut.graphql.tools.codegen.Origin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the operation documents of client mode: the operations and the named fragments, in the order of the sorted paths of the
 * documents and of the definitions inside each. A document that does not parse, an operation without a name, and an operation or a
 * fragment that is defined twice are diagnostics. A document is parsed like the compile-time checks parse it, with the same limit of
 * tokens, so both read the same nodes.
 */
final class OperationDocuments {

    // the limit of the compile-time checks: a document up to the size of a string constant must parse
    private static final ParserOptions OPTIONS = ParserOptions.newParserOptions().maxTokens(100_000).build();

    private OperationDocuments() {
    }

    static Parsed parse(List<SchemaSource> sources, List<Diagnostic> diagnostics) {
        List<OperationDefinition> operations = new ArrayList<>();
        Map<String, FragmentDefinition> fragments = new LinkedHashMap<>();
        Map<String, OperationDefinition> byName = new LinkedHashMap<>();
        Map<String, SchemaSource> byPath = new LinkedHashMap<>();
        Set<String> conflicting = new HashSet<>();
        for (SchemaSource source : sources) {
            SchemaSource first = byPath.putIfAbsent(source.path(), source);
            if (first != null && !first.text().equals(source.text()) && conflicting.add(source.path())) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.DUPLICATE_DEFINITION,
                        "The operation document " + source.path() + " is given twice with different content.", null,
                        new Origin(source.path(), 0, 0)));
            }
        }
        int defined = 0;
        for (SchemaSource source : byPath.values().stream().filter(it -> !conflicting.contains(it.path()))
                .sorted(Comparator.comparing(SchemaSource::path, SchemaLoader.UTF8_ORDER)).toList()) {
            Document document = parseDocument(source, diagnostics);
            if (document == null) {
                continue;
            }
            for (Definition<?> definition : document.getDefinitions()) {
                if (definition instanceof OperationDefinition operation) {
                    defined++;
                    addOperation(operation, operations, byName, diagnostics);
                } else if (definition instanceof FragmentDefinition fragment) {
                    FragmentDefinition first = fragments.putIfAbsent(fragment.getName(), fragment);
                    if (first != null) {
                        diagnostics.add(duplicate(Diagnostic.DUPLICATE_FRAGMENT, "fragment", fragment.getName(), first, fragment));
                    }
                }
            }
        }
        if (defined == 0 && diagnostics.stream().noneMatch(Diagnostic::isError)) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.NO_OPERATION_DOCUMENTS, byPath.isEmpty()
                    ? "Client mode has no operation document. Give files, text or a directory to the generator."
                    : "The operation documents " + byPath.keySet() + " define no operation, so client mode has nothing to generate.",
                    null, null));
        }
        return new Parsed(operations, fragments);
    }

    private static void addOperation(OperationDefinition operation, List<OperationDefinition> operations,
                                     Map<String, OperationDefinition> byName, List<Diagnostic> diagnostics) {
        if (operation.getName() == null) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.ANONYMOUS_OPERATION,
                    "The operation at " + SchemaLoader.origin(operation) + " has no name. Name it: the name is the name of its request class.",
                    null, SchemaLoader.origin(operation)));
            return;
        }
        OperationDefinition first = byName.putIfAbsent(operation.getName(), operation);
        if (first != null) {
            diagnostics.add(duplicate(Diagnostic.DUPLICATE_OPERATION, "operation", operation.getName(), first, operation));
            return;
        }
        operations.add(operation);
    }

    private static Diagnostic duplicate(String code, String what, String name, graphql.language.Node<?> first,
                                        graphql.language.Node<?> second) {
        return new Diagnostic(Diagnostic.Severity.ERROR, code, "The " + what + " '" + name + "' is defined twice: at "
                + SchemaLoader.origin(first) + " and at " + SchemaLoader.origin(second) + ".", null, SchemaLoader.origin(second));
    }

    private static Document parseDocument(SchemaSource source, List<Diagnostic> diagnostics) {
        String text = source.text().startsWith("﻿") ? source.text().substring(1) : source.text();
        if (text.lines().map(String::strip).allMatch(line -> line.isEmpty() || line.startsWith("#"))) {
            return null;
        }
        try {
            return new Parser().parseDocument(ParserEnvironment.newParserEnvironment()
                    .document(MultiSourceReader.newMultiSourceReader().string(text, source.path()).build())
                    .parserOptions(OPTIONS)
                    .build());
        } catch (InvalidSyntaxException e) {
            int line = e.getLocation() != null ? e.getLocation().getLine() : 0;
            int column = e.getLocation() != null ? e.getLocation().getColumn() : 0;
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.OPERATION_SYNTAX_ERROR,
                    "The operation document " + source.path() + " is not valid GraphQL: " + e.getMessage(), null,
                    new Origin(source.path(), line, column)));
            return null;
        }
    }

    /**
     * The definitions of the documents.
     *
     * @param operations the operations in the order of the documents
     * @param fragments  the fragments by name, in the order of the documents
     */
    record Parsed(List<OperationDefinition> operations, Map<String, FragmentDefinition> fragments) {
    }
}
