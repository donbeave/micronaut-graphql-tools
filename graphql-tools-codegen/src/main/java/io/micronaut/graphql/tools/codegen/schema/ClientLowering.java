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

import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.ListType;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import io.micronaut.graphql.tools.codegen.Diagnostic;
import io.micronaut.graphql.tools.codegen.Options;
import io.micronaut.graphql.tools.codegen.Origin;
import io.micronaut.graphql.tools.codegen.ir.AbstractType;
import io.micronaut.graphql.tools.codegen.ir.ModelType;
import io.micronaut.graphql.tools.codegen.ir.client.OperationDocument;
import io.micronaut.graphql.tools.codegen.ir.client.Payload;
import io.micronaut.graphql.tools.codegen.ir.client.SelectedArgument;
import io.micronaut.graphql.tools.codegen.ir.client.SelectedField;
import io.micronaut.graphql.tools.codegen.ir.client.Variable;
import io.micronaut.graphql.tools.codegen.ir.shared.AppliedDirective;
import io.micronaut.graphql.tools.codegen.ir.shared.BuiltinScalar;
import io.micronaut.graphql.tools.codegen.ir.shared.Docs;
import io.micronaut.graphql.tools.codegen.ir.shared.Names;
import io.micronaut.graphql.tools.codegen.ir.shared.Operation;
import io.micronaut.graphql.tools.codegen.ir.shared.Presence;
import io.micronaut.graphql.tools.codegen.ir.shared.TypeRef;
import io.micronaut.graphql.tools.codegen.schema.MergedSchema.MergedType;
import io.micronaut.graphql.tools.codegen.schema.MergedSchema.MergedType.Kind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The rules of client mode: they assemble the operation documents, validate them against the schema and lower them into the
 * operations, the fragments and the result models of the IR.
 *
 * <p>The result models follow the selection sets. A selection set on an object type is one record, named by the path from the
 * operation: {@code <Operation>Data} for the {@code data} object, and the path of the parent followed by the response key for a
 * field below it. A selection set on an interface or union is a sealed interface with one record for each object type that a type
 * condition of the selection singles out, and an {@code Other} record for every other object type. A named fragment is an interface
 * that the records which spread it implement. A field that a {@code @skip} or {@code @include} can remove from the response has a
 * nullable type, even when its schema type is non-null.</p>
 *
 * <p>A record implements an interface only when it can: every field of the interface has the same type in the record, and a field
 * with a selection set selects exactly what the interface selects there. Java generics are invariant, so a list of the records
 * of one selection is not a list of the interface of another. When a record cannot implement the interface of a fragment it spreads,
 * it keeps the fields and a warning says so.</p>
 */
final class ClientLowering {

    private static final String SKIP = "skip";
    private static final String INCLUDE = "include";
    private static final String SCHEMA_FIELD = "__schema";
    private static final String TYPE_FIELD = "__type";
    private static final String OTHER = "Other";
    private static final String DATA = "Data";
    private static final String VARIABLES = "Variables";

    private final Lowering lowering;
    private final MergedSchema schema;
    private final List<Diagnostic> diagnostics;
    private final Options options;
    private final String modelPackage;
    private final Map<String, Set<String>> supertypes = new HashMap<>();
    // the fragments with __typename injected, one instance for each name, so that the selections of two operations that spread a
    // fragment are the same nodes
    private final Map<String, FragmentDefinition> canonical = new TreeMap<>();
    private final Map<String, Interface> fragmentInterfaces = new HashMap<>();
    private final Map<String, io.micronaut.graphql.tools.codegen.ir.client.FragmentDefinition> fragmentNodes = new TreeMap<>();
    private final List<OperationDocument> operations = new ArrayList<>();
    private final List<ModelType> models = new ArrayList<>();
    private final List<AbstractType> abstractTypes = new ArrayList<>();
    private final Set<String> reached = new HashSet<>();

    ClientLowering(Lowering lowering, MergedSchema schema, List<Diagnostic> diagnostics) {
        this.lowering = lowering;
        this.schema = schema;
        this.diagnostics = diagnostics;
        this.options = lowering.options();
        this.modelPackage = options.effectiveModelPackage();
    }

    Result lower(OperationDocuments.Parsed parsed) {
        ClientSchema.Built built = ClientSchema.build(schema);
        if (built.schema() == null) {
            diagnostics.add(built.diagnostic());
            return result();
        }
        OperationAssembler assembler = new OperationAssembler(built.schema());
        Map<OperationDefinition, OperationAssembler.Assembled> assembled = new LinkedHashMap<>();
        for (OperationDefinition operation : parsed.operations()) {
            OperationAssembler.Assembled one = assembler.assemble(operation, parsed.fragments(), diagnostics);
            if (one != null) {
                assembled.put(operation, one);
            }
        }
        // an operation that failed does not stop the checks of the others: the IR is dropped when a diagnostic is an error
        assembled.forEach((operation, one) -> one.fragments().forEach(canonical::putIfAbsent));
        assembled.forEach(this::operation);
        closeInputs();
        return result();
    }

    private Result result() {
        return new Result(operations, new ArrayList<>(fragmentNodes.values()), models, abstractTypes, reached);
    }

    // ----- operations

    private void operation(OperationDefinition operation, OperationAssembler.Assembled assembled) {
        String name = operation.getName();
        String base = capitalize(name);
        Origin origin = SchemaLoader.origin(operation);
        Operation kind = Operation.valueOf(operation.getOperation().name());
        Names request = named(name, base);
        register(request, "request class of the operation '" + name + "'", origin);
        Names variablesType = named(name, base + VARIABLES);
        register(variablesType, "variables type of the operation '" + name + "'", origin);
        List<Variable> variables = new ArrayList<>();
        for (VariableDefinition variable : operation.getVariableDefinitions()) {
            Origin at = SchemaLoader.origin(variable);
            String where = "variable $" + variable.getName() + " of the operation '" + name + "' (" + at + ")";
            TypeRef type = shape(variable.getType(), true, (typeName, nullable) -> {
                reach(typeName);
                return lowering.ref(TypeName.newTypeName(typeName).build(), nullable, null, where, at);
            });
            String defaultValue = NodeText.value(variable.getDefaultValue());
            variables.add(new Variable(variable.getName(), type, defaultValue, Presence.of(type, defaultValue != null), at));
        }
        String root = rootType(kind);
        io.micronaut.graphql.tools.codegen.ir.client.SelectionSet selection = selection(assembled.operation().getSelectionSet(), root, origin);
        Flat flat = flatten(List.of(new Sub(assembled.operation().getSelectionSet(), false)), root);
        for (Group group : flat.groups.values()) {
            if (group.fieldName.equals(SCHEMA_FIELD) || group.fieldName.equals(TYPE_FIELD)) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.OPERATION_INVALID, "The operation '" + name
                        + "' selects the introspection field " + group.fieldName + " at " + originOf(group.nodes.get(0), origin)
                        + ", and the generator makes no result model of an introspection query. Send it with the low-level client.",
                        null, origin));
                return;
            }
        }
        TypeRef result;
        if (assembled.payload() == Payload.DATA) {
            Names data = named(root, base + DATA);
            record(base + DATA, base, root, flat, data, null, null, false, origin);
            result = new TypeRef.Reference(data, false);
        } else {
            result = rootField(base, root, flat.groups.values().iterator().next(), origin);
        }
        for (String fragment : assembled.fragments().keySet()) {
            fragmentInterface(fragment);
        }
        operations.add(new OperationDocument(request, kind, variables, variablesType, assembled.payload(), result, selection,
                assembled.text(), assembled.hash(), new ArrayList<>(assembled.fragments().keySet()), origin));
    }

    // an operation that selects one root field returns that field's value
    private TypeRef rootField(String base, String root, Group group, Origin origin) {
        FieldDefinition definition = definition(root, group.fieldName);
        if (definition == null) {
            // __typename: the name of the root type
            return fieldType(root, group, null, null);
        }
        boolean composite = composite(definition.getType());
        Names child = composite ? named(baseName(definition.getType()), base + capitalize(group.key)) : null;
        TypeRef type = fieldType(root, group, definition, child);
        if (composite) {
            derive(new Pending(child, base + capitalize(group.key), baseName(definition.getType()), selections(group), origin));
        }
        return type;
    }

    private String rootType(Operation kind) {
        MergedSchema.Root root = schema.roots.get(kind);
        return root == null ? kind.name().charAt(0) + kind.name().substring(1).toLowerCase(java.util.Locale.ROOT) : root.typeName();
    }

    // ----- the selection IR

    private io.micronaut.graphql.tools.codegen.ir.client.SelectionSet selection(SelectionSet set, String parentType, Origin fallback) {
        List<SelectedField> fields = new ArrayList<>();
        List<io.micronaut.graphql.tools.codegen.ir.client.InlineFragment> inline = new ArrayList<>();
        List<io.micronaut.graphql.tools.codegen.ir.client.FragmentSpread> spreads = new ArrayList<>();
        for (Selection<?> selection : set.getSelections()) {
            Origin origin = originOf(selection, fallback);
            if (selection instanceof Field field) {
                fields.add(selectedField(field, parentType, origin));
            } else if (selection instanceof InlineFragment fragment) {
                String condition = fragment.getTypeCondition() == null ? null : fragment.getTypeCondition().getName();
                inline.add(new io.micronaut.graphql.tools.codegen.ir.client.InlineFragment(condition,
                        selection(fragment.getSelectionSet(), condition == null ? parentType : condition, origin),
                        lowering.text().directives(fragment.getDirectives()), origin));
            } else if (selection instanceof FragmentSpread spread) {
                spreads.add(new io.micronaut.graphql.tools.codegen.ir.client.FragmentSpread(spread.getName(),
                        lowering.text().directives(spread.getDirectives()), origin));
            }
        }
        return new io.micronaut.graphql.tools.codegen.ir.client.SelectionSet(fields, inline, spreads);
    }

    private SelectedField selectedField(Field field, String parentType, Origin origin) {
        List<SelectedArgument> arguments = new ArrayList<>();
        for (Argument argument : field.getArguments()) {
            arguments.add(new SelectedArgument(argument.getName(), NodeText.value(argument.getValue())));
        }
        FieldDefinition definition = definition(parentType, field.getName());
        TypeRef type;
        io.micronaut.graphql.tools.codegen.ir.client.SelectionSet sub = null;
        if (definition == null) {
            type = new TypeRef.Builtin(BuiltinScalar.STRING, false);
        } else {
            String where = "field '" + parentType + "." + field.getName() + "' selected at " + origin;
            type = shape(definition.getType(), true, (typeName, nullable) -> isComposite(typeName)
                    ? new TypeRef.Reference(new Names(typeName, "", typeName), nullable)
                    : leaf(typeName, nullable, parentType + "." + field.getName(), where, origin));
            if (field.getSelectionSet() != null) {
                sub = selection(field.getSelectionSet(), baseName(definition.getType()), origin);
            }
        }
        return new SelectedField(field.getAlias(), field.getName(), arguments, type, sub, lowering.text().directives(field.getDirectives()),
                origin);
    }

    private io.micronaut.graphql.tools.codegen.ir.client.FragmentDefinition fragmentNode(String name) {
        FragmentDefinition fragment = canonical.get(name);
        Origin origin = SchemaLoader.origin(fragment);
        String condition = fragment.getTypeCondition().getName();
        return new io.micronaut.graphql.tools.codegen.ir.client.FragmentDefinition(named(name, name), condition,
                selection(fragment.getSelectionSet(), condition, origin), origin);
    }

    // ----- result models

    // a record for a selection that is on an object type, or for one object type of an abstract selection
    private void record(String path, String childPrefix, String scopeType, Flat flat, Names names, Interface sealed, Names sealedNames,
                        boolean union, Origin origin) {
        register(names, "result model '" + path + "'", origin);
        List<Interface> candidates = new ArrayList<>();
        if (sealed != null) {
            candidates.add(sealed);
        }
        for (String fragment : flat.fragments) {
            candidates.add(fragmentInterface(fragment));
        }
        Map<String, Entry> fixed = new LinkedHashMap<>();
        List<Names> implemented = new ArrayList<>();
        for (Interface candidate : candidates) {
            String problem = conflict(candidate, flat, fixed);
            if (problem == null) {
                candidate.keys().forEach(fixed::putIfAbsent);
                if (candidate != sealed) {
                    implemented.add(candidate.names());
                }
            } else {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING, Diagnostic.FRAGMENT_INTERFACE_SKIPPED, "The result model "
                        + names.simpleName() + " (" + origin + ") does not implement the interface " + candidate.names().simpleName()
                        + " of the fragment that it spreads: " + problem + " It keeps all the fields of the fragment.", null, origin));
            }
        }
        List<io.micronaut.graphql.tools.codegen.ir.Field> fields = new ArrayList<>();
        List<Pending> pending = new ArrayList<>();
        for (Group group : flat.groups.values()) {
            Entry entry = fixed.get(group.key);
            Origin at = originOf(group.nodes.get(0), origin);
            FieldDefinition definition = definition(scopeType, group.fieldName);
            if (definition != null) {
                lowering.checkField("field", path, group.key, at);
            }
            TypeRef type;
            if (entry != null) {
                type = entry.type();
            } else {
                Names child = null;
                if (definition != null && composite(definition.getType())) {
                    child = named(baseName(definition.getType()), childPrefix + capitalize(group.key));
                    pending.add(new Pending(child, childPrefix + capitalize(group.key), baseName(definition.getType()), selections(group), at));
                }
                type = fieldType(scopeType, group, definition, child);
            }
            fields.add(field(group, definition, type, at));
        }
        List<Names> memberOf = new ArrayList<>();
        if (sealedNames != null) {
            (union ? memberOf : implemented).add(0, sealedNames);
        }
        models.add(new ModelType(names, Docs.NONE, fields, memberOf, implemented, List.of(), origin));
        pending.forEach(this::derive);
    }

    // why a record cannot implement an interface, or null when it can
    private static String conflict(Interface candidate, Flat flat, Map<String, Entry> fixed) {
        for (Map.Entry<String, Entry> key : candidate.keys().entrySet()) {
            Group group = flat.groups.get(key.getKey());
            if (group == null) {
                return "it does not select the field " + key.getKey() + ".";
            }
            Entry known = fixed.get(key.getKey());
            if (known != null && !known.type().equals(key.getValue().type())) {
                return "the field " + key.getKey() + " has another type in an interface that it implements.";
            }
            if (key.getValue().child() != null && !key.getValue().nodes().equals(group.nodes)) {
                return "the field " + key.getKey() + " also selects fields that the fragment does not, and a list of one selection is"
                        + " not a list of another.";
            }
        }
        return null;
    }

    private io.micronaut.graphql.tools.codegen.ir.Field field(Group group, FieldDefinition definition, TypeRef type, Origin origin) {
        Docs docs = definition == null ? Docs.NONE : lowering.text().docs(definition, definition.getDescription(), definition.getDirectives());
        return new io.micronaut.graphql.tools.codegen.ir.Field(group.fieldName, group.key, type, false, List.of(), docs,
                directives(group.nodes.get(0)), origin);
    }

    private List<AppliedDirective> directives(Field field) {
        return lowering.text().directives(field.getDirectives());
    }

    // the type of a response key: the schema type with the nullability of the selection, and the generated type of a selection set
    private TypeRef fieldType(String scopeType, Group group, FieldDefinition definition, Names child) {
        TypeRef type;
        if (definition == null) {
            type = new TypeRef.Builtin(BuiltinScalar.STRING, false);
        } else {
            String where = "field '" + scopeType + "." + group.fieldName + "' selected at " + originOf(group.nodes.get(0), null);
            type = shape(definition.getType(), true, (typeName, nullable) -> isComposite(typeName)
                    ? new TypeRef.Reference(child, nullable)
                    : leaf(typeName, nullable, scopeType + "." + group.fieldName, where, originOf(group.nodes.get(0), null)));
        }
        return group.conditional ? nullable(type) : type;
    }

    private TypeRef leaf(String typeName, boolean nullable, String mappingKey, String where, Origin origin) {
        reach(typeName);
        return lowering.ref(TypeName.newTypeName(typeName).build(), nullable, mappingKey, where, origin);
    }

    // a selection set on an interface or a union: a sealed interface, a record for each object type that the selection singles out,
    // and a record for the others
    private void sealed(Pending pending) {
        MergedType type = schema.types.get(pending.typeName());
        boolean union = type.kind == Kind.UNION;
        String path = pending.path();
        Names names = pending.names();
        register(names, "sealed result model '" + path + "'", pending.origin());
        Flat abstractFlat = flatten(pending.sets(), pending.typeName());
        Map<String, Flat> variants = new LinkedHashMap<>();
        for (String variant : variants(pending.typeName(), pending.sets())) {
            variants.put(variant, flatten(pending.sets(), variant));
        }
        Map<String, Entry> keys = new LinkedHashMap<>();
        List<io.micronaut.graphql.tools.codegen.ir.Field> fields = new ArrayList<>();
        List<Pending> children = new ArrayList<>();
        for (Group group : abstractFlat.groups.values()) {
            FieldDefinition definition = definition(pending.typeName(), group.fieldName);
            boolean composite = definition != null && composite(definition.getType());
            // a list of the records of one selection is not a list of the records of another, so a field that a variant extends is not
            // declared on the interface
            if (composite && !variants.values().stream().allMatch(flat -> flat.groups.get(group.key) != null
                    && flat.groups.get(group.key).nodes.equals(group.nodes))) {
                continue;
            }
            Origin at = originOf(group.nodes.get(0), pending.origin());
            Names child = null;
            if (composite) {
                child = named(baseName(definition.getType()), path + capitalize(group.key));
                children.add(new Pending(child, path + capitalize(group.key), baseName(definition.getType()), selections(group), at));
            }
            TypeRef fieldType = fieldType(pending.typeName(), group, definition, child);
            keys.put(group.key, new Entry(fieldType, child, composite ? group.nodes : List.of()));
            fields.add(field(group, definition, fieldType, at));
        }
        Map<String, Names> memberNames = new LinkedHashMap<>();
        variants.keySet().forEach(variant -> memberNames.put(variant, named(variant, path + variant)));
        Names other = named(pending.typeName(), path + OTHER);
        List<Names> members = new ArrayList<>(memberNames.values());
        members.add(other);
        abstractTypes.add(new AbstractType(union ? AbstractType.Kind.UNION : AbstractType.Kind.INTERFACE, names, Docs.NONE, fields,
                List.of(), members, true, List.of(), pending.origin()));
        children.forEach(this::derive);
        Interface sealed = new Interface(names, keys);
        variants.forEach((variant, flat) -> record(path + variant, path + variant, variant, flat, memberNames.get(variant), sealed, names,
                union, pending.origin()));
        record(path + OTHER, path + OTHER, pending.typeName(), abstractFlat, other, sealed, names, union, pending.origin());
    }

    private void derive(Pending pending) {
        MergedType type = schema.types.get(pending.typeName());
        if (type.kind == Kind.OBJECT) {
            Flat flat = flatten(pending.sets(), pending.typeName());
            record(pending.path(), pending.path(), pending.typeName(), flat, pending.names(), null, null, false, pending.origin());
        } else {
            sealed(pending);
        }
    }

    // the interface of a fragment, made once: its fields with the types that the fragment's own type gives them
    private Interface fragmentInterface(String name) {
        Interface known = fragmentInterfaces.get(name);
        if (known != null) {
            return known;
        }
        FragmentDefinition fragment = canonical.get(name);
        io.micronaut.graphql.tools.codegen.ir.client.FragmentDefinition node = fragmentNode(name);
        fragmentNodes.put(name, node);
        Names names = node.names();
        String condition = node.typeCondition();
        register(names, "interface of the fragment '" + name + "'", node.origin());
        Flat flat = flatten(List.of(new Sub(fragment.getSelectionSet(), false)), condition);
        Map<String, Entry> keys = new LinkedHashMap<>();
        List<io.micronaut.graphql.tools.codegen.ir.Field> fields = new ArrayList<>();
        List<Pending> pending = new ArrayList<>();
        for (Group group : flat.groups.values()) {
            FieldDefinition definition = definition(condition, group.fieldName);
            Origin at = originOf(group.nodes.get(0), node.origin());
            boolean composite = definition != null && composite(definition.getType());
            Names child = null;
            if (composite) {
                child = named(baseName(definition.getType()), name + capitalize(group.key));
                pending.add(new Pending(child, name + capitalize(group.key), baseName(definition.getType()), selections(group), at));
            }
            TypeRef type = fieldType(condition, group, definition, child);
            keys.put(group.key, new Entry(type, child, composite ? group.nodes : List.of()));
            fields.add(field(group, definition, type, at));
        }
        Interface created = new Interface(names, keys);
        fragmentInterfaces.put(name, created);
        abstractTypes.add(new AbstractType(AbstractType.Kind.INTERFACE, names, Docs.NONE, fields, List.of(), List.of(), false, List.of(),
                node.origin()));
        pending.forEach(this::derive);
        return created;
    }

    // ----- walking a selection

    private Flat flatten(List<Sub> sets, String scope) {
        Flat flat = new Flat();
        for (Sub sub : sets) {
            walk(sub.set(), scope, sub.conditional(), flat, new HashSet<>());
        }
        return flat;
    }

    private void walk(SelectionSet set, String scope, boolean conditional, Flat flat, Set<String> active) {
        for (Selection<?> selection : set.getSelections()) {
            if (selection instanceof Field field) {
                Group group = flat.groups.computeIfAbsent(field.getResultKey(), key -> new Group(key, field.getName()));
                boolean removable = conditional || conditional(field.getDirectives());
                int known = -1;
                for (int i = 0; i < group.nodes.size(); i++) {
                    if (group.nodes.get(i) == field) {
                        known = i;
                    }
                }
                if (known < 0) {
                    group.nodes.add(field);
                    group.removable.add(removable);
                } else {
                    group.removable.set(known, group.removable.get(known) && removable);
                }
                group.conditional &= removable;
            } else if (selection instanceof InlineFragment fragment) {
                String condition = fragment.getTypeCondition() == null ? null : fragment.getTypeCondition().getName();
                if (condition == null || applies(condition, scope)) {
                    walk(fragment.getSelectionSet(), scope, conditional || conditional(fragment.getDirectives()), flat, active);
                }
            } else if (selection instanceof FragmentSpread spread) {
                FragmentDefinition fragment = canonical.get(spread.getName());
                if (fragment != null && applies(fragment.getTypeCondition().getName(), scope) && active.add(spread.getName())) {
                    boolean skipped = conditional || conditional(spread.getDirectives());
                    if (!skipped) {
                        flat.fragments.add(spread.getName());
                    }
                    walk(fragment.getSelectionSet(), scope, skipped, flat, active);
                    active.remove(spread.getName());
                }
            }
        }
    }

    // the object types of an abstract selection that a type condition singles out, in schema order
    private List<String> variants(String scope, List<Sub> sets) {
        Set<String> conditions = new LinkedHashSet<>();
        for (Sub sub : sets) {
            conditions(sub.set(), scope, conditions, new HashSet<>());
        }
        List<String> variants = new ArrayList<>();
        for (String type : possible(scope)) {
            if (conditions.stream().anyMatch(condition -> possible(condition).contains(type))) {
                variants.add(type);
            }
        }
        return variants;
    }

    private void conditions(SelectionSet set, String scope, Set<String> found, Set<String> seen) {
        for (Selection<?> selection : set.getSelections()) {
            if (selection instanceof InlineFragment fragment) {
                if (fragment.getTypeCondition() != null && !applies(fragment.getTypeCondition().getName(), scope)) {
                    found.add(fragment.getTypeCondition().getName());
                }
                conditions(fragment.getSelectionSet(), scope, found, seen);
            } else if (selection instanceof FragmentSpread spread && seen.add(spread.getName())) {
                FragmentDefinition fragment = canonical.get(spread.getName());
                if (fragment != null) {
                    if (!applies(fragment.getTypeCondition().getName(), scope)) {
                        found.add(fragment.getTypeCondition().getName());
                    }
                    conditions(fragment.getSelectionSet(), scope, found, seen);
                }
            }
        }
    }

    // the selections below a response key. When every occurrence of the key can be removed, the object below exists exactly when its
    // fields do, so they are present; when another occurrence always stays, the fields of an occurrence that can be removed can be
    // missing from the object
    private static List<Sub> selections(Group group) {
        List<Sub> sets = new ArrayList<>();
        for (int i = 0; i < group.nodes.size(); i++) {
            if (group.nodes.get(i).getSelectionSet() != null) {
                sets.add(new Sub(group.nodes.get(i).getSelectionSet(), !group.conditional && group.removable.get(i)));
            }
        }
        return sets;
    }

    private static boolean conditional(List<Directive> directives) {
        return directives.stream().anyMatch(directive -> directive.getName().equals(SKIP) || directive.getName().equals(INCLUDE));
    }

    // ----- types of the schema

    private boolean applies(String condition, String scope) {
        return condition.equals(scope) || supertypes(scope).contains(condition);
    }

    // the interfaces that a type implements, directly or through other interfaces, and the unions that list it
    private Set<String> supertypes(String name) {
        Set<String> known = supertypes.get(name);
        if (known != null) {
            return known;
        }
        Set<String> found = new LinkedHashSet<>();
        Deque<String> work = new ArrayDeque<>();
        work.push(name);
        while (!work.isEmpty()) {
            MergedType type = schema.types.get(work.pop());
            if (type != null && (type.kind == Kind.OBJECT || type.kind == Kind.INTERFACE)) {
                for (String implemented : type.implemented) {
                    if (found.add(implemented)) {
                        work.push(implemented);
                    }
                }
            }
        }
        MergedType type = schema.types.get(name);
        if (type != null && type.kind == Kind.OBJECT) {
            schema.types.values().stream().filter(it -> it.kind == Kind.UNION && it.members.contains(name))
                    .forEach(it -> found.add(it.name));
        }
        supertypes.put(name, found);
        return found;
    }

    // the object types that a type can be at run time, in schema order
    private List<String> possible(String name) {
        MergedType type = schema.types.get(name);
        List<String> found = new ArrayList<>();
        if (type == null) {
            return found;
        }
        if (type.kind == Kind.OBJECT) {
            found.add(name);
        } else if (type.kind == Kind.INTERFACE) {
            schema.types.values().stream().filter(it -> it.kind == Kind.OBJECT && supertypes(it.name).contains(name))
                    .forEach(it -> found.add(it.name));
        } else if (type.kind == Kind.UNION) {
            type.members.stream().filter(it -> schema.types.get(it) != null && schema.types.get(it).kind == Kind.OBJECT).forEach(found::add);
        }
        return found;
    }

    // the field of an object type or an interface, or null for __typename
    private FieldDefinition definition(String typeName, String fieldName) {
        MergedType type = schema.types.get(typeName);
        if (type == null || fieldName.equals(OperationAssembler.TYPENAME)) {
            return null;
        }
        for (FieldDefinition field : type.fields) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    private boolean isComposite(String typeName) {
        MergedType type = schema.types.get(typeName);
        return type != null && (type.kind == Kind.OBJECT || type.kind == Kind.INTERFACE || type.kind == Kind.UNION);
    }

    private boolean composite(Type<?> type) {
        return isComposite(baseName(type));
    }

    private static String baseName(Type<?> type) {
        if (type instanceof NonNullType nonNull) {
            return baseName(nonNull.getType());
        } else if (type instanceof ListType list) {
            return baseName(list.getType());
        }
        return ((TypeName) type).getName();
    }

    private static TypeRef shape(Type<?> type, boolean nullable, Leaf leaf) {
        if (type instanceof NonNullType nonNull) {
            return shape(nonNull.getType(), false, leaf);
        } else if (type instanceof ListType list) {
            return new TypeRef.ListOf(shape(list.getType(), true, leaf), nullable);
        }
        return leaf.make(((TypeName) type).getName(), nullable);
    }

    private static TypeRef nullable(TypeRef type) {
        if (type instanceof TypeRef.Builtin builtin) {
            return new TypeRef.Builtin(builtin.scalar(), true);
        } else if (type instanceof TypeRef.Mapped mapped) {
            return new TypeRef.Mapped(mapped.className(), true);
        } else if (type instanceof TypeRef.Reference reference) {
            return new TypeRef.Reference(reference.target(), true);
        }
        return new TypeRef.ListOf(((TypeRef.ListOf) type).element(), true);
    }

    // ----- names

    private Names named(String sdlName, String path) {
        return new Names(sdlName, modelPackage, options.typeNamePrefix() + path + options.typeNameSuffix());
    }

    private void register(Names names, String description, Origin origin) {
        lowering.unit(names.packageName(), names.simpleName(), description, origin);
    }

    private static String capitalize(String name) {
        int first = name.codePointAt(0);
        return new StringBuilder().appendCodePoint(Character.toUpperCase(first)).append(name, Character.charCount(first), name.length())
                .toString();
    }

    private static Origin originOf(Node<?> node, Origin fallback) {
        return node.getSourceLocation() == null && fallback != null ? fallback : SchemaLoader.origin(node);
    }

    // ----- the enums, scalars and inputs that an operation reaches

    private void reach(String typeName) {
        MergedType type = schema.types.get(typeName);
        if (type != null && (type.kind == Kind.ENUM || type.kind == Kind.SCALAR || type.kind == Kind.INPUT)) {
            reached.add(typeName);
        }
    }

    private void closeInputs() {
        Deque<String> work = new ArrayDeque<>(reached);
        while (!work.isEmpty()) {
            MergedType type = schema.types.get(work.pop());
            if (type != null && type.kind == Kind.INPUT) {
                for (graphql.language.InputValueDefinition field : type.inputFields) {
                    String target = baseName(field.getType());
                    MergedType targetType = schema.types.get(target);
                    if (targetType != null && reached.add(target)) {
                        work.push(target);
                    }
                }
            }
        }
    }

    /**
     * What client mode lowered.
     *
     * @param operations    the operations in the order of their documents
     * @param fragments     the fragments that the operations use, by name
     * @param models        the result records
     * @param abstractTypes the sealed interfaces of the abstract selections and the interfaces of the fragments
     * @param reached       the names of the schema types that an operation reaches: its variables, the input objects below them, and
     *                      the enums and scalars that a selection or an input reads
     */
    record Result(List<OperationDocument> operations, List<io.micronaut.graphql.tools.codegen.ir.client.FragmentDefinition> fragments,
                  List<ModelType> models, List<AbstractType> abstractTypes, Set<String> reached) {
    }

    // what an interface says about a response key: its type, the generated type of its selection, and the nodes of that selection
    private record Entry(TypeRef type, Names child, List<Field> nodes) {
    }

    private record Interface(Names names, Map<String, Entry> keys) {
    }

    private record Pending(Names names, String path, String typeName, List<Sub> sets, Origin origin) {
    }

    // a selection set and whether skip or include can remove it from the response
    private record Sub(SelectionSet set, boolean conditional) {
    }

    private static final class Group {
        final String key;
        final String fieldName;
        final List<Field> nodes = new ArrayList<>();
        // for each node, whether skip or include can remove it, on the field or on a fragment around it
        final List<Boolean> removable = new ArrayList<>();
        boolean conditional = true;

        Group(String key, String fieldName) {
            this.key = key;
            this.fieldName = fieldName;
        }
    }

    private static final class Flat {
        final Map<String, Group> groups = new LinkedHashMap<>();
        final Set<String> fragments = new LinkedHashSet<>();
    }

    private interface Leaf {
        TypeRef make(String typeName, boolean nullable);
    }
}
