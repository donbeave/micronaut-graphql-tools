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

import graphql.language.AstPrinter;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.DocumentLocation;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSourceException;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Turns the operation methods of a compilation into assembled, validated operations. A request names its document inline or
 * by operation name; the operation and the fragments it uses (those of its own document first, then those of the
 * {@code *.graphql} files of its schema directory) become one document that graphql-java validates against the client schema.
 *
 * <p>Everything is lazy and happens once per compilation: a schema directory is listed on the first request that needs it,
 * a schema is built when the first operation of it is validated, and the documents of a directory are read when the first
 * request selects an operation by name or uses a fragment that its own document does not define. A cause that several requests
 * share ({@code client-schema-missing}, {@code client-schema-invalid}, a document file that does not parse) is reported by the
 * first of them only. With {@code checks} off no schema is built and nothing is validated: the document is still parsed,
 * assembled and measured, because the runtime reads its type, its variables and its payload mode from the metadata.</p>
 */
@Internal
public final class ClientOperations {

    /**
     * The longest document that the annotation metadata can hold: a class file stores a string constant as at most 65,535 bytes
     * of modified UTF-8.
     */
    public static final int MAX_DOCUMENT_BYTES = 65_535;

    private static final Pattern NAME = Pattern.compile("[_A-Za-z][_0-9A-Za-z]*");

    private final ClientDirectories directories;
    private final boolean checks;
    private final long stackBytes;
    private final Validator validator = new Validator();
    private final Map<String, Resolution> resolutions = new HashMap<>();
    private final Map<String, SchemaState> states = new HashMap<>();
    private int operations;
    private int built;

    /**
     * @param directories the client directories and the server schema directories
     * @param checks      whether the documents are validated against the client schema
     * @param stackBytes  the stack of the thread that builds a schema ({@link ClientSchemaBuilder#STACK_BYTES})
     */
    public ClientOperations(ClientDirectories directories, boolean checks, long stackBytes) {
        this.directories = directories;
        this.checks = checks;
        this.stackBytes = stackBytes;
    }

    /**
     * @return the number of operations that were assembled
     */
    public int operations() {
        return operations;
    }

    /**
     * @return the number of schemas that were built
     */
    public int schemasBuilt() {
        return built;
    }

    /**
     * @param request one operation method
     * @return the assembled operation, or the findings
     */
    public ClientOutcome resolve(ClientRequest request) {
        List<Finding> findings = new ArrayList<>();
        boolean inline = !request.value().isEmpty() && !NAME.matcher(request.value()).matches();
        SchemaState state = null;
        // the schema validates and its directory holds the named documents; an inline document with checks off needs neither
        if (checks || !inline) {
            state = state(request, findings);
            if (state == null) {
                return ClientOutcome.failed(findings);
            }
        }
        Selected selected = inline ? selectInline(request) : selectNamed(request, state, findings);
        if (selected.operation == null) {
            findings.addAll(selected.findings);
            return ClientOutcome.failed(findings);
        }
        if (request.get() && selected.operation.getOperation() != OperationDefinition.Operation.QUERY) {
            findings.add(ClientRules.getOnNonQuery(request.member(), selected.operation.getOperation().name().toLowerCase(Locale.ROOT)));
        }
        Map<String, FragmentDefinition> fragments = new TreeMap<>();
        if (!collectFragments(request, selected, fragments, state, findings)) {
            return ClientOutcome.failed(findings);
        }
        OperationDefinition operation = selected.operation;
        if (checks) {
            GraphQLSchema schema = schema(request, state, findings);
            if (schema == null) {
                return ClientOutcome.failed(findings);
            }
            Document assembled = Document.newDocument().definitions(definitions(operation, fragments)).build();
            for (ValidationError error : validator.validateDocument(schema, assembled, Locale.ENGLISH)) {
                findings.add(ClientDocuments.invalid(request.member(), error.getMessage(), location(error)));
            }
            if (findings.isEmpty()) {
                ClientTypenames.Injected injected = ClientTypenames.inject(schema, operation, fragments);
                injected.reserved().forEach(field -> findings.add(ClientDocuments.invalid(request.member(), "the alias " + ClientTypenames.TYPENAME
                        + " is reserved: the selection of an interface or union gets the field " + ClientTypenames.TYPENAME + " for the type "
                        + "name of its value, and this field has the same response key", ClientDocuments.location(field.getSourceLocation()))));
                operation = injected.operation();
                fragments = injected.fragments();
            }
        }
        if (!findings.isEmpty()) {
            return ClientOutcome.failed(findings);
        }
        String document = AstPrinter.printAstCompact(Document.newDocument().definitions(definitions(operation, fragments)).build());
        int bytes = modifiedUtf8Length(document);
        if (bytes > MAX_DOCUMENT_BYTES) {
            return ClientOutcome.failed(List.of(new Finding(BindingCode.CLIENT_DOCUMENT_TOO_LARGE.getCode(), request.member(),
                    "the assembled document is " + bytes + " bytes, and the annotation metadata holds at most " + MAX_DOCUMENT_BYTES
                    + " bytes of a string", null, null, null, "split the operation, or select fewer fields; the assembled document "
                    + "is the operation and the fragments it uses")));
        }
        operations++;
        return ClientOutcome.of(new ClientOperation(document, operation.getName() == null ? "" : operation.getName(),
                operation.getOperation().name().toLowerCase(Locale.ROOT), variables(operation), payload(operation, fragments)));
    }

    private Selected selectInline(ClientRequest request) {
        ClientDocuments.Parsed parsed = ClientDocuments.parse(request.value(), null);
        if (parsed.document() == null) {
            return Selected.failed(ClientDocuments.invalid(request.member(), parsed.message(), parsed.location()));
        }
        List<OperationDefinition> defined = parsed.document().getDefinitionsOfType(OperationDefinition.class);
        List<OperationDefinition> candidates = request.name().isEmpty() ? defined
                : defined.stream().filter(it -> request.name().equals(it.getName())).toList();
        Map<String, FragmentDefinition> own = new HashMap<>();
        for (FragmentDefinition fragment : parsed.document().getDefinitionsOfType(FragmentDefinition.class)) {
            if (own.putIfAbsent(fragment.getName(), fragment) != null) {
                return Selected.failed(ClientDocuments.invalid(request.member(), "the document defines the fragment "
                        + fragment.getName() + " twice", ClientDocuments.location(fragment.getSourceLocation())));
            }
        }
        if (candidates.size() == 1) {
            return Selected.of(candidates.get(0), own);
        }
        List<String> names = defined.stream().map(it -> it.getName() == null ? "(anonymous)" : it.getName()).toList();
        if (candidates.isEmpty()) {
            return Selected.failed(notFound(request, request.name().isEmpty() ? "the document defines no operation"
                    : "the document defines no operation named " + request.name() + ", only " + names));
        }
        return Selected.failed(ambiguous(request, "the document defines the operations " + names + " and the name member names "
                + (request.name().isEmpty() ? "none" : "one of them twice")));
    }

    private Selected selectNamed(ClientRequest request, SchemaState state, List<Finding> findings) {
        ClientDocuments.Index index = index(state, findings);
        if (state.layout.documentsProblem() != null) {
            return Selected.failed();
        }
        String name = !request.name().isEmpty() ? request.name() : request.value().isEmpty() ? request.methodName() : request.value();
        List<OperationDefinition> defined = index.operations().getOrDefault(name, List.of());
        if (defined.size() == 1) {
            return Selected.of(defined.get(0), Map.of());
        }
        if (defined.isEmpty()) {
            return Selected.failed(notFound(request, "no operation named " + name + " in the document files of the client schema "
                    + state.layout.name() + ", whose directories are " + state.layout.documentDirs()));
        }
        return Selected.failed(ambiguous(request, "the operation " + name + " is defined " + defined.size() + " times, in "
                + defined.stream().map(it -> String.valueOf(it.getSourceLocation().getSourceName())).toList()));
    }

    // the fragments that the operation uses, transitively: the document's own first, then the document files of the schema
    private boolean collectFragments(ClientRequest request, Selected selected, Map<String, FragmentDefinition> collected,
                                     @Nullable SchemaState state, List<Finding> findings) {
        boolean complete = true;
        Set<String> seen = new HashSet<>();
        Deque<Node<?>> work = new ArrayDeque<>();
        work.push(selected.operation);
        while (!work.isEmpty()) {
            Node<?> node = work.pop();
            if (node instanceof FragmentSpread spread && seen.add(spread.getName())) {
                FragmentDefinition fragment = selected.own.get(spread.getName());
                SchemaState from = null;
                if (fragment == null) {
                    from = state != null ? state : quietState(request);
                    if (from != null) {
                        List<FragmentDefinition> defined = index(from, findings).fragments().get(spread.getName());
                        fragment = defined == null ? null : defined.get(0);
                        // a directory that cannot be read may hold another definition of the fragment, so every request that reads
                        // the documents fails, and the problem is reported once
                        if (from.layout.documentsProblem() != null) {
                            complete = false;
                            continue;
                        }
                    }
                }
                if (fragment == null) {
                    complete = false;
                    findings.add(ClientDocuments.invalid(request.member(), "the operation uses the fragment " + spread.getName()
                            + ", which neither its document nor the document files of the client schema define",
                            ClientDocuments.location(spread.getSourceLocation())));
                    continue;
                }
                collected.put(spread.getName(), fragment);
                work.push(fragment);
            }
            for (Node<?> child : node.getChildren()) {
                work.push(child);
            }
        }
        return complete;
    }

    private static List<graphql.language.Definition> definitions(OperationDefinition operation, Map<String, FragmentDefinition> fragments) {
        List<graphql.language.Definition> definitions = new ArrayList<>();
        definitions.add(operation);
        definitions.addAll(fragments.values());
        return definitions;
    }

    private static List<String> variables(OperationDefinition operation) {
        List<String> names = new ArrayList<>();
        for (VariableDefinition variable : operation.getVariableDefinitions()) {
            names.add(variable.getName());
        }
        return names;
    }

    // one root field, by response key, through fragment spreads and inline fragments: the payload is that field's value; else data
    private static PayloadMode payload(OperationDefinition operation, Map<String, FragmentDefinition> fragments) {
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
        return keys.size() == 1 ? PayloadMode.FIELD : PayloadMode.DATA;
    }

    private static @Nullable DocumentLocation location(ValidationError error) {
        return error.getLocations() == null || error.getLocations().isEmpty() ? null
                : ClientDocuments.location(error.getLocations().get(0));
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

    private static Finding notFound(ClientRequest request, String sentence) {
        return new Finding(BindingCode.CLIENT_OPERATION_NOT_FOUND.getCode(), request.member(), sentence, null, null, null,
                "name an operation of the document with the name member, or write the operation in a " + ClientDocuments.EXTENSION
                + " file of the client directory of the schema");
    }

    private static Finding ambiguous(ClientRequest request, String sentence) {
        return new Finding(BindingCode.CLIENT_OPERATION_AMBIGUOUS.getCode(), request.member(), sentence, null, null, null,
                "set the name member of @GraphQLOperation to the one operation to run");
    }

    // the layout of the schema of the request; a problem is added to the findings by the first request that meets it
    private @Nullable SchemaState state(ClientRequest request, List<Finding> findings) {
        Resolution resolution = resolution(request);
        if (resolution.problem != null) {
            findings.add(resolution.problem);
            resolution.problem = null;
        }
        return resolution.state;
    }

    // the layout without the problem: an inline document with checks off only looks for fragments in the directory
    private @Nullable SchemaState quietState(ClientRequest request) {
        return resolution(request).state;
    }

    private Resolution resolution(ClientRequest request) {
        Resolution resolution = resolutions.get(request.schema());
        if (resolution == null) {
            ClientDirectories.Resolved resolved = directories.resolve(request.schema(), request.member(), checks);
            SchemaState state = resolved.layout() == null ? null
                    : states.computeIfAbsent(resolved.layout().name(), name -> new SchemaState(resolved.layout()));
            resolution = new Resolution(state, resolved.problem());
            resolutions.put(request.schema(), resolution);
        }
        return resolution;
    }

    private ClientDocuments.Index index(SchemaState state, List<Finding> findings) {
        if (state.index == null) {
            state.index = ClientDocuments.index(state.layout.documentDirs());
        }
        if (!state.indexReported) {
            state.indexReported = true;
            findings.addAll(state.index.problems());
            if (state.layout.documentsProblem() != null) {
                findings.add(state.layout.documentsProblem());
            }
        }
        return state.index;
    }

    private @Nullable GraphQLSchema schema(ClientRequest request, SchemaState state, List<Finding> findings) {
        if (!state.schemaTried) {
            state.schemaTried = true;
            List<SchemaSources.Source> sources = null;
            try {
                sources = ClientSchemaBuilder.sources(state.layout.schemaDirs());
            } catch (SchemaSourceException e) {
                state.schemaProblem = new Finding(BindingCode.CLIENT_SCHEMA_INVALID.getCode(), state.layout.name(),
                        "the client schema " + state.layout.name() + " cannot be read: " + e.getMessage(), null, null, null,
                        "correct the schema directory, or compile with micronaut.graphql.tools.processor.client.checks=false");
            }
            if (sources != null && sources.isEmpty()) {
                state.schemaProblem = new Finding(BindingCode.CLIENT_SCHEMA_MISSING.getCode(), request.member(),
                        "the client schema " + state.layout.name() + " has no *" + SchemaSources.EXTENSION + " file in "
                        + state.layout.schemaDirs(), null, null, null, "add the schema as *" + SchemaSources.EXTENSION
                        + " files to the directory");
            } else if (sources != null) {
                built++;
                ClientSchemaBuilder.Built result = ClientSchemaBuilder.build(state.layout.name(), sources, stackBytes);
                state.schema = result.schema();
                state.schemaProblem = result.problem();
            }
        }
        if (state.schemaProblem != null) {
            findings.add(state.schemaProblem);
            state.schemaProblem = null;
        }
        return state.schema;
    }

    private record Selected(@Nullable OperationDefinition operation, Map<String, FragmentDefinition> own, List<Finding> findings) {

        static Selected of(OperationDefinition operation, Map<String, FragmentDefinition> own) {
            return new Selected(operation, own, List.of());
        }

        static Selected failed(Finding finding) {
            return new Selected(null, Map.of(), List.of(finding));
        }

        // the cause was reported through the findings of the request, or by an earlier request
        static Selected failed() {
            return new Selected(null, Map.of(), List.of());
        }
    }

    private static final class Resolution {
        final @Nullable SchemaState state;
        @Nullable Finding problem;

        Resolution(@Nullable SchemaState state, @Nullable Finding problem) {
            this.state = state;
            this.problem = problem;
        }
    }

    private static final class SchemaState {
        final ClientDirectories.Layout layout;
        boolean schemaTried;
        @Nullable GraphQLSchema schema;
        @Nullable Finding schemaProblem;
        @Nullable ClientDocuments.Index index;
        boolean indexReported;

        SchemaState(ClientDirectories.Layout layout) {
            this.layout = layout;
        }
    }
}
