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

import graphql.language.AstPrinter;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import io.micronaut.graphql.tools.codegen.Diagnostic;
import io.micronaut.graphql.tools.codegen.ir.client.Payload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Assembles the document of one operation exactly as the compile-time checks of the processor do, so that the generated request
 * class sends the document that the checks validated: the operation and the fragments it uses (transitively, by name) become one
 * document that graphql-java validates against the schema; {@code __typename} is then put first in every selection set of an
 * interface or union that does not select it; and the document is printed compactly, the operation first and then the fragments in
 * the order of their names. A test compares the text with the output of the checks.
 */
final class OperationAssembler {

    /** The longest document that a string constant of a class file can hold, in bytes of modified UTF-8. */
    static final int MAX_DOCUMENT_BYTES = 65_535;

    static final String TYPENAME = "__typename";

    private final GraphQLSchema schema;
    private final Validator validator = new Validator();

    OperationAssembler(GraphQLSchema schema) {
        this.schema = schema;
    }

    /**
     * @param operation   the operation
     * @param definitions the named fragments of all documents
     * @param diagnostics where the problems are added
     * @return the assembled operation, or {@code null} when a diagnostic was added
     */
    Assembled assemble(OperationDefinition operation, Map<String, FragmentDefinition> definitions, List<Diagnostic> diagnostics) {
        int before = diagnostics.size();
        Map<String, FragmentDefinition> fragments = new TreeMap<>();
        collect(operation, definitions, fragments, diagnostics);
        if (diagnostics.size() > before) {
            return null;
        }
        Document assembled = Document.newDocument().definitions(definitions(operation, fragments)).build();
        for (ValidationError error : validator.validateDocument(schema, assembled, Locale.ENGLISH)) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.OPERATION_INVALID, "The operation '" + operation.getName()
                    + "' is not valid against the schema" + at(error) + ": " + error.getMessage(), null,
                    error.getLocations() == null || error.getLocations().isEmpty() ? SchemaLoader.origin(operation)
                            : SchemaLoader.origin(operation, error.getLocations().get(0))));
        }
        if (diagnostics.size() > before) {
            return null;
        }
        Injected injected = inject(operation, fragments);
        for (Field field : injected.reserved()) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.RESERVED_TYPENAME_ALIAS, "The alias " + TYPENAME
                    + " at " + SchemaLoader.origin(field) + " is reserved: the selection of an interface or union gets the field "
                    + TYPENAME + " for the type name of its value, and this field has the same response key as the fields of such a"
                    + " selection. Rename the alias.", null, SchemaLoader.origin(field)));
        }
        if (diagnostics.size() > before) {
            return null;
        }
        String text = AstPrinter.printAstCompact(Document.newDocument().definitions(definitions(injected.operation(), injected.fragments()))
                .build());
        int bytes = modifiedUtf8Length(text);
        if (bytes > MAX_DOCUMENT_BYTES) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.DOCUMENT_TOO_LARGE, "The assembled document of the"
                    + " operation '" + operation.getName() + "' is " + bytes + " bytes, and the generated class holds it as a string"
                    + " constant of at most " + MAX_DOCUMENT_BYTES + " bytes. Split the operation, or select fewer fields: the document"
                    + " is the operation and the fragments it uses.", null, SchemaLoader.origin(operation)));
            return null;
        }
        return new Assembled(injected.operation(), injected.fragments(), text, sha256(text),
                payload(injected.operation(), injected.fragments()));
    }

    private static String at(ValidationError error) {
        return error.getLocations() == null || error.getLocations().isEmpty() ? "" : " (" + error.getLocations().get(0).getSourceName()
                + ":" + error.getLocations().get(0).getLine() + ":" + error.getLocations().get(0).getColumn() + ")";
    }

    // the fragments that the operation uses, transitively
    private static void collect(OperationDefinition operation, Map<String, FragmentDefinition> definitions,
                                Map<String, FragmentDefinition> collected, List<Diagnostic> diagnostics) {
        Set<String> seen = new HashSet<>();
        Deque<Node<?>> work = new ArrayDeque<>();
        work.push(operation);
        while (!work.isEmpty()) {
            Node<?> node = work.pop();
            if (node instanceof FragmentSpread spread && seen.add(spread.getName())) {
                FragmentDefinition fragment = definitions.get(spread.getName());
                if (fragment == null) {
                    diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.OPERATION_INVALID, "The operation '"
                            + operation.getName() + "' uses the fragment " + spread.getName() + " at " + SchemaLoader.origin(spread)
                            + ", which no operation document defines.", null, SchemaLoader.origin(spread)));
                    continue;
                }
                collected.put(spread.getName(), fragment);
                work.push(fragment);
            }
            for (Node<?> child : node.getChildren()) {
                work.push(child);
            }
        }
    }

    private static List<Definition> definitions(OperationDefinition operation, Map<String, FragmentDefinition> fragments) {
        List<Definition> definitions = new ArrayList<>();
        definitions.add(operation);
        definitions.addAll(fragments.values());
        return definitions;
    }

    // one root field, by response key, through fragment spreads and inline fragments: the payload is that field's value; else data
    private static Payload payload(OperationDefinition operation, Map<String, FragmentDefinition> fragments) {
        Set<String> keys = new HashSet<>();
        Set<String> spread = new HashSet<>();
        Deque<SelectionSet> work = new ArrayDeque<>();
        work.push(operation.getSelectionSet());
        while (!work.isEmpty()) {
            for (Selection<?> selection : work.pop().getSelections()) {
                if (selection instanceof Field field) {
                    keys.add(field.getResultKey());
                } else if (selection instanceof InlineFragment inline) {
                    work.push(inline.getSelectionSet());
                } else if (selection instanceof FragmentSpread fragment && spread.add(fragment.getName())) {
                    FragmentDefinition definition = fragments.get(fragment.getName());
                    if (definition != null) {
                        work.push(definition.getSelectionSet());
                    }
                }
            }
        }
        return keys.size() == 1 ? Payload.FIELD : Payload.DATA;
    }

    // the length of the modified UTF-8 that a class file uses for a string constant
    static int modifiedUtf8Length(String text) {
        int bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            bytes += c >= 0x0001 && c <= 0x007F ? 1 : c <= 0x07FF ? 2 : 3;
        }
        return bytes;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // the walk builds copies; the nodes of the document are never changed
    private Injected inject(OperationDefinition operation, Map<String, FragmentDefinition> fragments) {
        GraphQLType root = switch (operation.getOperation()) {
            case QUERY -> schema.getQueryType();
            case MUTATION -> schema.getMutationType();
            case SUBSCRIPTION -> schema.getSubscriptionType();
        };
        List<Field> reserved = new ArrayList<>();
        OperationDefinition changed = operation;
        if (root != null && operation.getSelectionSet() != null) {
            changed = operation.transform(builder -> builder.selectionSet(selections(operation.getSelectionSet(), root, reserved)));
        }
        Map<String, FragmentDefinition> changedFragments = new TreeMap<>();
        for (Map.Entry<String, FragmentDefinition> entry : fragments.entrySet()) {
            FragmentDefinition fragment = entry.getValue();
            GraphQLType type = schema.getType(fragment.getTypeCondition().getName());
            changedFragments.put(entry.getKey(), type == null ? fragment
                    : fragment.transform(builder -> builder.selectionSet(selections(fragment.getSelectionSet(), type, reserved))));
        }
        return new Injected(changed, changedFragments, reserved);
    }

    private SelectionSet selections(SelectionSet set, GraphQLType parent, List<Field> reserved) {
        List<Selection> result = new ArrayList<>();
        boolean selected = false;
        for (Selection<?> selection : set.getSelections()) {
            if (selection instanceof Field field) {
                selected |= isTypename(field);
                // the alias is reserved in the whole document: a field under a concrete fragment shares the response keys of the
                // abstract selection that the fragment is in
                if (!field.getName().equals(TYPENAME) && TYPENAME.equals(field.getResultKey())) {
                    reserved.add(field);
                }
                result.add(inside(field, parent, reserved));
            } else if (selection instanceof InlineFragment fragment) {
                GraphQLType type = fragment.getTypeCondition() == null ? parent : schema.getType(fragment.getTypeCondition().getName());
                result.add(type == null ? fragment : fragment.transform(builder -> builder.selectionSet(selections(
                        fragment.getSelectionSet(), type, reserved))));
            } else {
                result.add(selection);
            }
        }
        if (!selected && isAbstract(parent)) {
            result.add(0, Field.newField(TYPENAME).build());
        }
        return set.transform(builder -> builder.selections(result));
    }

    private Field inside(Field field, GraphQLType parent, List<Field> reserved) {
        if (field.getSelectionSet() == null || !(parent instanceof GraphQLFieldsContainer container)) {
            return field;
        }
        GraphQLFieldDefinition definition = container.getFieldDefinition(field.getName());
        if (definition == null) {
            return field;
        }
        GraphQLType child = GraphQLTypeUtil.unwrapAll(definition.getType());
        return field.transform(builder -> builder.selectionSet(selections(field.getSelectionSet(), child, reserved)));
    }

    private static boolean isTypename(Field field) {
        return TYPENAME.equals(field.getName()) && field.getAlias() == null && field.getDirectives().isEmpty();
    }

    private static boolean isAbstract(GraphQLType type) {
        return type instanceof GraphQLInterfaceType || type instanceof GraphQLUnionType;
    }

    /**
     * An assembled operation.
     *
     * @param operation the operation with {@code __typename} injected
     * @param fragments the fragments it uses with {@code __typename} injected, by name
     * @param text      the printed document
     * @param hash      the SHA-256 of the text as lower-case hex
     * @param payload   what the result is
     */
    record Assembled(OperationDefinition operation, Map<String, FragmentDefinition> fragments, String text, String hash, Payload payload) {
    }

    private record Injected(OperationDefinition operation, Map<String, FragmentDefinition> fragments, List<Field> reserved) {
    }
}
