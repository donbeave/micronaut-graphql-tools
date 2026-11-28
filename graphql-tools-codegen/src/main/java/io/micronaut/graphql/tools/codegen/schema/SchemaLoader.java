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
import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.DirectiveDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.Node;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SDLDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.SchemaExtensionDefinition;
import graphql.language.SourceLocation;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.MultiSourceReader;
import graphql.parser.Parser;
import graphql.parser.ParserEnvironment;
import graphql.parser.ParserOptions;
import io.micronaut.graphql.tools.codegen.Diagnostic;
import io.micronaut.graphql.tools.codegen.schema.MergedSchema.MergedType.Kind;
import io.micronaut.graphql.tools.codegen.schema.MergedSchema.MergedType;
import io.micronaut.graphql.tools.codegen.Origin;
import io.micronaut.graphql.tools.codegen.ir.shared.Operation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The loader: reads schema sources with the graphql-java parser only and merges them. The merge order is fixed so the result does
 * not depend on the order that the files were given in. The sources are sorted by path in UTF-8 byte order and a path that appears
 * twice is read once. Definitions keep their order inside a file. Every extension is merged after the base definitions of all
 * files, in the order of its file and its position, so an extension may come before its base in the files. A file that holds
 * only executable definitions or only comments is skipped, as the runtime and the compile-time checks skip it.
 */
final class SchemaLoader {

    static final Comparator<String> UTF8_ORDER = (a, b) -> Arrays.compareUnsigned(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));

    private final List<Diagnostic> diagnostics;
    private final MergedSchema schema = new MergedSchema();
    private final List<String> skipped = new ArrayList<>();
    private final List<TypeDefinition<?>> extensions = new ArrayList<>();
    private final List<SchemaDefinition> schemaDefinitions = new ArrayList<>();
    private boolean explicitSchema;

    private SchemaLoader(List<Diagnostic> diagnostics) {
        this.diagnostics = diagnostics;
    }

    static Loaded load(List<SchemaSource> sources, List<Diagnostic> diagnostics) {
        SchemaLoader loader = new SchemaLoader(diagnostics);
        Map<String, SchemaSource> byPath = new LinkedHashMap<>();
        Set<String> conflicting = new HashSet<>();
        for (SchemaSource source : sources) {
            SchemaSource first = byPath.putIfAbsent(source.path(), source);
            if (first != null && !first.text().equals(source.text()) && conflicting.add(source.path())) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.DUPLICATE_DEFINITION,
                        "The schema source " + source.path() + " is given twice with different content.", null,
                        new Origin(source.path(), 0, 0)));
            }
        }
        byPath.values().stream().filter(source -> !conflicting.contains(source.path()))
                .sorted(Comparator.comparing(SchemaSource::path, UTF8_ORDER)).forEach(loader::read);
        loader.mergeExtensions();
        loader.resolveRoots();
        return new Loaded(loader.schema, List.copyOf(loader.skipped));
    }

    private void read(SchemaSource source) {
        String text = source.text().startsWith("\uFEFF") ? source.text().substring(1) : source.text();
        if (text.lines().map(String::strip).allMatch(line -> line.isEmpty() || line.startsWith("#"))) {
            skipped.add(source.path());
            return;
        }
        Document document;
        try {
            document = new Parser().parseDocument(ParserEnvironment.newParserEnvironment()
                    .document(MultiSourceReader.newMultiSourceReader().string(text, source.path()).build())
                    .parserOptions(ParserOptions.getDefaultSdlParserOptions())
                    .build());
        } catch (InvalidSyntaxException e) {
            int line = e.getLocation() != null ? e.getLocation().getLine() : 0;
            int column = e.getLocation() != null ? e.getLocation().getColumn() : 0;
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.SCHEMA_SYNTAX_ERROR,
                    "The schema file " + source.path() + " is not valid GraphQL: " + e.getMessage(), null,
                    new Origin(source.path(), line, column)));
            return;
        }
        List<Definition> definitions = document.getDefinitions();
        boolean schemaContent = false;
        for (Definition<?> definition : definitions) {
            if (definition instanceof SDLDefinition<?> sdl) {
                schema.definitions.add(sdl);
            }
            if (isExtension(definition)) {
                extensions.add((TypeDefinition<?>) definition);
                schemaContent = true;
            } else if (definition instanceof TypeDefinition<?> type) {
                addBase(type);
                schemaContent = true;
            } else if (definition instanceof SchemaDefinition schemaDefinition) {
                schemaDefinitions.add(schemaDefinition);
                explicitSchema |= !(definition instanceof SchemaExtensionDefinition);
                schemaContent = true;
            } else if (definition instanceof DirectiveDefinition) {
                schemaContent = true;
            }
        }
        if (!schemaContent) {
            skipped.add(source.path());
        }
    }

    private static boolean isExtension(Definition<?> definition) {
        return definition instanceof ObjectTypeExtensionDefinition || definition instanceof InterfaceTypeExtensionDefinition
                || definition instanceof UnionTypeExtensionDefinition || definition instanceof EnumTypeExtensionDefinition
                || definition instanceof InputObjectTypeExtensionDefinition || definition instanceof ScalarTypeExtensionDefinition;
    }

    private static Kind kind(TypeDefinition<?> type) {
        if (type instanceof ObjectTypeDefinition) {
            return Kind.OBJECT;
        } else if (type instanceof InterfaceTypeDefinition) {
            return Kind.INTERFACE;
        } else if (type instanceof UnionTypeDefinition) {
            return Kind.UNION;
        } else if (type instanceof EnumTypeDefinition) {
            return Kind.ENUM;
        } else if (type instanceof InputObjectTypeDefinition) {
            return Kind.INPUT;
        }
        return Kind.SCALAR;
    }

    private static String kindName(Kind kind) {
        return kind == Kind.INPUT ? "input type" : kind.name().toLowerCase(Locale.ROOT) + (kind == Kind.SCALAR ? "" : " type");
    }

    static Origin origin(Node<?> node) {
        SourceLocation location = node.getSourceLocation();
        if (location == null) {
            return new Origin("<unknown>", 0, 0);
        }
        return new Origin(location.getSourceName() == null ? "<unknown>" : location.getSourceName(), location.getLine(),
                location.getColumn());
    }

    static Origin origin(Node<?> node, SourceLocation at) {
        return new Origin(at.getSourceName() == null ? origin(node).path() : at.getSourceName(), at.getLine(), at.getColumn());
    }

    private void addBase(TypeDefinition<?> type) {
        MergedType existing = schema.types.get(type.getName());
        if (existing != null) {
            diagnostics.add(duplicate("type", type.getName(), existing.origin, origin(type)));
            return;
        }
        MergedType merged = new MergedType(type.getName(), kind(type), type, origin(type));
        schema.types.put(type.getName(), merged);
        absorb(merged, type);
    }

    private void mergeExtensions() {
        for (TypeDefinition<?> extension : extensions) {
            MergedType base = schema.types.get(extension.getName());
            Origin origin = origin(extension);
            if (base == null) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.EXTENSION_WITHOUT_BASE,
                        "The extension of the type '" + extension.getName() + "' at " + origin
                                + " has no base definition of that name in the schema files.", null, origin));
            } else if (base.kind != kind(extension)) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.EXTENSION_KIND_MISMATCH,
                        "The extension at " + origin + " is an extension of an " + kindName(kind(extension)) + ", but '"
                                + extension.getName() + "' is an " + kindName(base.kind) + " (defined at " + base.origin + ").",
                        null, origin));
            } else {
                absorb(base, extension);
            }
        }
    }

    private void absorb(MergedType into, TypeDefinition<?> definition) {
        into.directives.addAll(definition.getDirectives());
        if (definition instanceof ObjectTypeDefinition object) {
            object.getFieldDefinitions().forEach(field -> declareField(into, field));
            into.fields.addAll(object.getFieldDefinitions());
            names(object.getImplements(), into.implemented);
        } else if (definition instanceof InterfaceTypeDefinition type) {
            type.getFieldDefinitions().forEach(field -> declareField(into, field));
            into.fields.addAll(type.getFieldDefinitions());
            names(type.getImplements(), into.implemented);
        } else if (definition instanceof UnionTypeDefinition union) {
            names(union.getMemberTypes(), into.members);
        } else if (definition instanceof EnumTypeDefinition type) {
            type.getEnumValueDefinitions().forEach(value -> declare(into, value.getName(), value, "enum value"));
            into.values.addAll(type.getEnumValueDefinitions());
        } else if (definition instanceof InputObjectTypeDefinition input) {
            input.getInputValueDefinitions().forEach(field -> declare(into, field.getName(), field, "field"));
            into.inputFields.addAll(input.getInputValueDefinitions());
        }
    }

    private void declareField(MergedType into, FieldDefinition field) {
        declare(into, field.getName(), field, "field");
        Map<String, Node<?>> arguments = new HashMap<>();
        for (InputValueDefinition argument : field.getInputValueDefinitions()) {
            Node<?> first = arguments.putIfAbsent(argument.getName(), argument);
            if (first != null) {
                diagnostics.add(duplicate("argument", into.name + "." + field.getName() + "(" + argument.getName() + ")", origin(first),
                        origin(argument)));
            }
        }
    }

    private void declare(MergedType into, String name, Node<?> node, String what) {
        Node<?> existing = into.declared.putIfAbsent(name, node);
        if (existing != null) {
            diagnostics.add(duplicate(what, into.name + "." + name, origin(existing), origin(node)));
        }
    }

    private static void names(List<? extends Type> types, Collection<String> into) {
        for (Type<?> type : types) {
            if (type instanceof TypeName name) {
                into.add(name.getName());
            }
        }
    }

    private static Diagnostic duplicate(String what, String name, Origin first, Origin second) {
        return new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.DUPLICATE_DEFINITION,
                "The " + what + " '" + name + "' is defined twice: at " + first + " and at " + second + ".", null, second);
    }

    private void resolveRoots() {
        for (SchemaDefinition definition : schemaDefinitions) {
            for (OperationTypeDefinition operation : definition.getOperationTypeDefinitions()) {
                Operation key = Operation.valueOf(operation.getName().toUpperCase(Locale.ROOT));
                Origin origin = origin(operation);
                MergedSchema.Root existing = schema.roots.get(key);
                if (existing != null) {
                    diagnostics.add(duplicate("root operation", operation.getName(), existing.origin(), origin));
                } else {
                    schema.roots.put(key, new MergedSchema.Root(operation.getTypeName().getName(), origin));
                }
            }
        }
        if (explicitSchema) {
            return;
        }
        MergedType query = schema.types.get("Query");
        if (!schema.roots.containsKey(Operation.QUERY) && (query == null || query.kind != Kind.OBJECT)) {
            return;
        }
        for (Operation operation : Operation.values()) {
            String name = operation.name().charAt(0) + operation.name().substring(1).toLowerCase(Locale.ROOT);
            MergedType type = schema.types.get(name);
            if (type != null && type.kind == Kind.OBJECT) {
                schema.roots.putIfAbsent(operation, new MergedSchema.Root(name, type.origin));
            }
        }
    }

    record Loaded(MergedSchema schema, List<String> skipped) {
    }
}
