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

import graphql.language.Description;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import io.micronaut.graphql.tools.codegen.Diagnostic;
import io.micronaut.graphql.tools.codegen.schema.MergedSchema.MergedType.Kind;
import io.micronaut.graphql.tools.codegen.schema.MergedSchema.MergedType;
import io.micronaut.graphql.tools.codegen.NameConstants;
import io.micronaut.graphql.tools.codegen.Options;
import io.micronaut.graphql.tools.codegen.Origin;
import io.micronaut.graphql.tools.codegen.SealedAbstractTypes;
import io.micronaut.graphql.tools.codegen.ir.AbstractType;
import io.micronaut.graphql.tools.codegen.ir.Argument;
import io.micronaut.graphql.tools.codegen.ir.ContractMethod;
import io.micronaut.graphql.tools.codegen.ir.EnumType;
import io.micronaut.graphql.tools.codegen.ir.EnumValue;
import io.micronaut.graphql.tools.codegen.ir.Field;
import io.micronaut.graphql.tools.codegen.ir.InputField;
import io.micronaut.graphql.tools.codegen.ir.InputType;
import io.micronaut.graphql.tools.codegen.ir.Ir;
import io.micronaut.graphql.tools.codegen.ir.ModelType;
import io.micronaut.graphql.tools.codegen.ir.NameClass;
import io.micronaut.graphql.tools.codegen.ir.NameConstant;
import io.micronaut.graphql.tools.codegen.ir.ResolverContract;
import io.micronaut.graphql.tools.codegen.ir.ScalarType;
import io.micronaut.graphql.tools.codegen.ir.shared.AppliedDirective;
import io.micronaut.graphql.tools.codegen.ir.shared.BuiltinScalar;
import io.micronaut.graphql.tools.codegen.ir.shared.Coordinate;
import io.micronaut.graphql.tools.codegen.ir.shared.Docs;
import io.micronaut.graphql.tools.codegen.ir.shared.Names;
import io.micronaut.graphql.tools.codegen.ir.shared.Operation;
import io.micronaut.graphql.tools.codegen.ir.shared.TypeRef;
import io.micronaut.graphql.tools.codegen.ir.shared.Wrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rules: they lower the merged definitions and the options into the IR. They apply the names, the type mappings, the
 * interface selection and the default contract grouping, and report every problem as a diagnostic instead of stopping at the
 * first one. After this step every decision is in the IR.
 */
final class Lowering {

    static final String TYPE_NAMES_CLASS = "GraphQLTypeNames";
    static final String NAMES_SUFFIX = "Names";
    static final String TYPE_CONSTANT = "TYPE";

    private static final Map<String, BuiltinScalar> BUILTIN = Map.of(
            "ID", BuiltinScalar.ID, "String", BuiltinScalar.STRING, "Int", BuiltinScalar.INT, "Float", BuiltinScalar.FLOAT,
            "Boolean", BuiltinScalar.BOOLEAN);

    private static final Map<Operation, String> MARKERS = Map.of(
            Operation.QUERY, "io.micronaut.graphql.tools.resolver.QueryResolver",
            Operation.MUTATION, "io.micronaut.graphql.tools.resolver.MutationResolver",
            Operation.SUBSCRIPTION, "io.micronaut.graphql.tools.resolver.SubscriptionResolver");

    private static final Set<String> ROOT_TYPE_NAMES = Set.of("Query", "Mutation", "Subscription");

    private static final Set<String> OBJECT_METHODS = Set.of(
            "clone", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait");

    private final Options options;
    private final Map<String, String> inherited;
    private final MergedSchema schema;
    private final List<Diagnostic> diagnostics;
    private final NodeText text;
    private final Map<String, Names> names = new HashMap<>();
    private final Map<String, Operation> rootTypes = new HashMap<>();
    private final Map<String, List<String>> unionsOf = new HashMap<>();
    private final Map<String, List<String>> implementers = new HashMap<>();
    private final Map<String, List<Field>> interfaceFields = new HashMap<>();
    private final Set<String> resolving = new HashSet<>();
    private final Set<String> selectedObjects;
    private final List<Unit> units = new ArrayList<>();
    private final Set<String> usedMappings = new HashSet<>();

    private Lowering(Options options, MergedSchema schema, Map<String, String> inherited, List<Diagnostic> diagnostics) {
        this.options = options;
        this.inherited = inherited;
        this.schema = schema;
        this.diagnostics = diagnostics;
        this.text = new NodeText(options.commentsAsDescriptions());
        this.selectedObjects = new HashSet<>(options.interfaceTypes());
    }

    static Ir lower(Options options, MergedSchema schema, Map<String, String> inherited, List<Diagnostic> diagnostics) {
        return new Lowering(options, schema, inherited, diagnostics).lower();
    }

    private Ir lower() {
        resolveRoots();
        nameTypes();
        relate();
        List<ScalarType> scalars = new ArrayList<>();
        List<EnumType> enums = new ArrayList<>();
        List<InputType> inputs = new ArrayList<>();
        List<ModelType> models = new ArrayList<>();
        List<AbstractType> abstractTypes = new ArrayList<>();
        for (MergedType type : schema.types.values()) {
            switch (type.kind) {
                case SCALAR -> scalar(type, scalars);
                case ENUM -> enumType(type, enums);
                case INPUT -> inputType(type, inputs);
                case OBJECT -> objectType(type, models, abstractTypes);
                case INTERFACE -> interfaceType(type, abstractTypes);
                case UNION -> unionType(type, abstractTypes);
                default -> throw new IllegalStateException(type.kind.name());
            }
        }
        List<ResolverContract> contracts = contracts();
        List<NameClass> nameClasses = nameClasses();
        collisions();
        checkOptionEntries();
        return new Ir(enums, inputs, models, abstractTypes, scalars, contracts, nameClasses);
    }

    private boolean isRoot(MergedType type) {
        return rootTypes.containsKey(type.name);
    }

    private String mapping(String name) {
        String mapped = options.typeMappings().get(name);
        if (mapped == null) {
            mapped = inherited.get(name);
        }
        if (mapped == null && !BUILTIN.containsKey(name)) {
            MergedType type = schema.types.get(name);
            if (type == null || type.kind == Kind.SCALAR) {
                mapped = ScalarPresets.presets().get(name);
            }
        }
        return mapped;
    }

    private boolean external(String name) {
        return mapping(name) != null;
    }

    private void error(String code, String message, Origin origin) {
        diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, code, message, null, origin));
    }

    private void checkOptionEntries() {
        for (String key : new TreeSet<>(options.typeMappings().keySet())) {
            int dot = key.indexOf('.');
            boolean known;
            if (dot < 0) {
                known = schema.types.containsKey(key) || BUILTIN.containsKey(key) || usedMappings.contains(key);
            } else {
                MergedType owner = schema.types.get(key.substring(0, dot));
                String field = key.substring(dot + 1);
                checkFieldMapping(key, owner, field);
                known = usedMappings.contains(key) || owner != null && (owner.fields.stream().anyMatch(it -> it.getName().equals(field))
                        || owner.inputFields.stream().anyMatch(it -> it.getName().equals(field)));
            }
            if (!known) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING, Diagnostic.UNUSED_TYPE_MAPPING,
                        "The option typeMappings." + key + " names no type or field of the schema, so it maps nothing.",
                        "typeMappings." + key, null));
            }
        }
        for (int i = 0; i < options.interfaceTypes().size(); i++) {
            String name = options.interfaceTypes().get(i);
            MergedType type = schema.types.get(name);
            if (type == null || type.kind != Kind.OBJECT) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING, Diagnostic.UNUSED_INTERFACE_TYPE,
                        "The option interfaceTypes." + i + " names '" + name + "', which is not an object type of the schema.",
                        "interfaceTypes." + i, null));
            }
        }
    }

    private void checkFieldMapping(String key, MergedType owner, String field) {
        if (owner == null) {
            return;
        }
        Type<?> type = null;
        Origin origin = null;
        for (FieldDefinition definition : owner.fields) {
            if (definition.getName().equals(field)) {
                type = definition.getType();
                origin = SchemaLoader.origin(definition);
            }
        }
        for (InputValueDefinition definition : owner.inputFields) {
            if (definition.getName().equals(field)) {
                type = definition.getType();
                origin = SchemaLoader.origin(definition);
            }
        }
        if (type == null) {
            return;
        }
        String target = baseName(type);
        MergedType targetType = schema.types.get(target);
        if (targetType != null && targetType.kind != Kind.SCALAR) {
            error(Diagnostic.FIELD_MAPPING_NOT_SCALAR, "The option typeMappings." + key + " maps the field '" + key + "' (" + origin
                    + "), whose type '" + target + "' is not a scalar. A GraphQL type has one Java type, so map the type with the"
                    + " option typeMappings." + target + " instead.", origin);
        }
    }

    private static String baseName(Type<?> type) {
        if (type instanceof NonNullType nonNull) {
            return baseName(nonNull.getType());
        } else if (type instanceof ListType list) {
            return baseName(list.getType());
        }
        return ((TypeName) type).getName();
    }

    private boolean warnMappedMembers(MergedType owner, Iterable<String> members) {
        boolean found = false;
        for (String member : members) {
            MergedType type = schema.types.get(member);
            if (type != null && type.kind != Kind.SCALAR && external(member)) {
                found = true;
                if (options.sealedAbstractTypes() != SealedAbstractTypes.AUTO) {
                    continue;
                }
                diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING, Diagnostic.MAPPED_MEMBER, "The "
                        + owner.kind.name().toLowerCase(Locale.ROOT) + " '" + owner.name + "' (" + owner.origin + ") has the member '"
                        + member + "', which typeMappings." + member + " maps to the external class " + mapping(member)
                        + ". That class cannot implement a generated type, so '" + owner.name + "' is generated as a plain interface,"
                        + " not a sealed one, and the application registers the class as a member itself.",
                        "typeMappings." + member, owner.origin));
            }
        }
        return found;
    }

    private void resolveRoots() {
        schema.roots.forEach((operation, root) -> {
            MergedType type = schema.types.get(root.typeName());
            if (type == null || type.kind != Kind.OBJECT) {
                error(Diagnostic.UNKNOWN_TYPE, "The " + operation.name().toLowerCase(Locale.ROOT) + " root type '" + root.typeName()
                        + "' at " + root.origin() + " is not an object type of the schema files.", root.origin());
            } else {
                rootTypes.putIfAbsent(root.typeName(), operation);
            }
        });
    }

    private void nameTypes() {
        String modelPackage = options.effectiveModelPackage();
        for (MergedType type : schema.types.values()) {
            boolean generated;
            if (type.kind == Kind.SCALAR) {
                generated = external(type.name) && !BUILTIN.containsKey(type.name);
            } else {
                generated = !isRoot(type) && !external(type.name);
            }
            if (generated) {
                names.put(type.name, new Names(type.name, modelPackage,
                        options.typeNamePrefix() + type.name + options.typeNameSuffix()));
            }
        }
    }

    private void relate() {
        for (MergedType type : schema.types.values()) {
            if (type.kind == Kind.UNION) {
                for (String member : type.members) {
                    MergedType target = schema.types.get(member);
                    if (target == null) {
                        error(Diagnostic.UNKNOWN_TYPE, "The union '" + type.name + "' (" + type.origin + ") lists the member '" + member
                                + "', which is not defined in the schema files.", type.origin);
                    } else if (target.kind != Kind.OBJECT) {
                        error(Diagnostic.INVALID_UNION_MEMBER, "The union '" + type.name + "' (" + type.origin + ") lists the member '"
                                + member + "', which is " + article(target.kind) + ", not an object type. A union has object types as members.",
                                type.origin);
                    }
                    unionsOf.computeIfAbsent(member, key -> new ArrayList<>()).add(type.name);
                }
            } else if (type.kind == Kind.OBJECT || type.kind == Kind.INTERFACE) {
                for (String implemented : type.implemented) {
                    MergedType target = schema.types.get(implemented);
                    if (target == null) {
                        error(Diagnostic.UNKNOWN_TYPE, "The type '" + type.name + "' (" + type.origin + ") implements '" + implemented
                                + "', which is not defined in the schema files.", type.origin);
                    } else if (target.kind != Kind.INTERFACE) {
                        error(Diagnostic.INVALID_IMPLEMENTS, "The " + type.kind.name().toLowerCase(Locale.ROOT) + " type '" + type.name
                                + "' (" + type.origin + ") implements '" + implemented + "', which is " + article(target.kind)
                                + ", not an interface. Only an interface can be implemented.", type.origin);
                    }
                    implementers.computeIfAbsent(implemented, key -> new ArrayList<>()).add(type.name);
                }
            }
        }
    }

    private static String article(Kind kind) {
        return switch (kind) {
            case OBJECT -> "an object type";
            case INTERFACE -> "an interface type";
            case UNION -> "a union type";
            case ENUM -> "an enum type";
            case INPUT -> "an input type";
            case SCALAR -> "a scalar";
        };
    }

    private List<Names> generatedNames(Iterable<String> typeNames) {
        List<Names> found = new ArrayList<>();
        for (String name : typeNames) {
            Names generated = names.get(name);
            if (generated != null && schema.types.get(name).kind != Kind.SCALAR) {
                found.add(generated);
            }
        }
        return found;
    }

    private void scalar(MergedType type, List<ScalarType> scalars) {
        if (BUILTIN.containsKey(type.name)) {
            return;
        }
        if (!external(type.name)) {
            error(Diagnostic.UNMAPPED_SCALAR, "The custom scalar '" + type.name + "' (" + type.origin + ") has no Java class. Map it with the"
                    + " option typeMappings." + type.name + ", for example typeMappings." + type.name
                    + "=java.time.LocalDate. The generator never falls back to String.", type.origin);
            return;
        }
        scalars.add(new ScalarType(names.get(type.name), text.docs(type.base, description(type), type.directives),
                mapping(type.name), text.directives(type.directives), type.origin));
    }

    private static Description description(MergedType type) {
        return switch (type.kind) {
            case OBJECT -> ((ObjectTypeDefinition) type.base).getDescription();
            case INTERFACE -> ((InterfaceTypeDefinition) type.base).getDescription();
            case UNION -> ((UnionTypeDefinition) type.base).getDescription();
            case ENUM -> ((EnumTypeDefinition) type.base).getDescription();
            case INPUT -> ((InputObjectTypeDefinition) type.base).getDescription();
            case SCALAR -> ((ScalarTypeDefinition) type.base).getDescription();
        };
    }

    private void enumType(MergedType type, List<EnumType> enums) {
        if (!names.containsKey(type.name)) {
            return;
        }
        List<EnumValue> values = new ArrayList<>(type.values.size());
        for (EnumValueDefinition value : type.values) {
            Origin origin = SchemaLoader.origin(value);
            checkReserved("enum value", type.name + "." + value.getName(), value.getName(), origin);
            values.add(new EnumValue(value.getName(), value.getName(), text.docs(value, value.getDescription(), value.getDirectives()),
                    text.directives(value.getDirectives()), origin));
        }
        register(type, "enum type");
        enums.add(new EnumType(names.get(type.name), text.docs(type.base, description(type), type.directives), values,
                text.directives(type.directives), type.origin));
    }

    private void inputType(MergedType type, List<InputType> inputs) {
        if (!names.containsKey(type.name)) {
            return;
        }
        List<InputField> fields = new ArrayList<>(type.inputFields.size());
        for (InputValueDefinition field : type.inputFields) {
            Origin origin = SchemaLoader.origin(field);
            checkField("field", type.name, field.getName(), origin);
            fields.add(new InputField(field.getName(), field.getName(),
                    ref(field.getType(), type.name + "." + field.getName(), "field", origin),
                    NodeText.value(field.getDefaultValue()), text.docs(field, field.getDescription(), field.getDirectives()),
                    text.directives(field.getDirectives()), origin));
        }
        register(type, "input type");
        inputs.add(new InputType(names.get(type.name), text.docs(type.base, description(type), type.directives), fields,
                text.directives(type.directives), type.origin));
    }

    private void objectType(MergedType type, List<ModelType> models, List<AbstractType> abstractTypes) {
        Names generated = names.get(type.name);
        if (generated == null) {
            return;
        }
        List<Field> fields = fields(type);
        List<Names> implemented = generatedNames(type.implemented);
        Docs docs = text.docs(type.base, description(type), type.directives);
        List<AppliedDirective> directives = text.directives(type.directives);
        if (selectedObjects.contains(type.name)) {
            register(type, "object type");
            abstractTypes.add(new AbstractType(AbstractType.Kind.OBJECT, generated, docs, fields, implemented, List.of(), false,
                    directives, type.origin));
        } else {
            register(type, "object type");
            models.add(new ModelType(generated, docs, fields, generatedNames(unionsOf.getOrDefault(type.name, List.of())), implemented,
                    directives, type.origin));
        }
    }

    private void interfaceType(MergedType type, List<AbstractType> abstractTypes) {
        Names generated = names.get(type.name);
        if (generated == null) {
            return;
        }
        List<String> implementing = implementers.getOrDefault(type.name, List.of());
        List<Names> members = generatedNames(implementing);
        warnMappedMembers(type, implementing);
        register(type, "interface type");
        abstractTypes.add(new AbstractType(AbstractType.Kind.INTERFACE, generated,
                text.docs(type.base, description(type), type.directives), fields(type), generatedNames(type.implemented), members,
                sealed(members, implementing.size()), text.directives(type.directives), type.origin));
    }

    private void unionType(MergedType type, List<AbstractType> abstractTypes) {
        Names generated = names.get(type.name);
        if (generated == null) {
            return;
        }
        List<Names> members = generatedNames(type.members);
        boolean mappedMember = warnMappedMembers(type, type.members);
        boolean selectedMember = warnSelectedMembers(type, mappedMember);
        register(type, "union type");
        abstractTypes.add(new AbstractType(AbstractType.Kind.UNION, generated,
                text.docs(type.base, description(type), type.directives), List.of(), List.of(), members,
                !selectedMember && sealed(members, type.members.size()), text.directives(type.directives), type.origin));
    }

    private boolean sealed(List<Names> members, int declared) {
        return options.sealedAbstractTypes() == SealedAbstractTypes.AUTO && !members.isEmpty() && members.size() == declared;
    }

    private boolean warnSelectedMembers(MergedType union, boolean reported) {
        boolean found = false;
        for (String member : union.members) {
            if (selectedObjects.contains(member) && names.containsKey(member)) {
                found = true;
                if (!reported && options.sealedAbstractTypes() == SealedAbstractTypes.AUTO) {
                    reported = true;
                    diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING, Diagnostic.SELECTED_MEMBER, "The union '" + union.name
                            + "' (" + union.origin + ") has the member '" + member + "', which the option interfaceTypes generates as an"
                            + " interface. An interface cannot implement the union, so '" + union.name + "' is generated as a plain"
                            + " interface, not a sealed one, and a class that implements '" + member + "' implements the union itself.",
                            "interfaceTypes", union.origin));
                }
            }
        }
        return found;
    }

    private List<Field> fields(MergedType owner) {
        boolean isInterface = owner.kind == Kind.INTERFACE;
        if (isInterface) {
            List<Field> known = interfaceFields.get(owner.name);
            if (known != null) {
                return known;
            }
            resolving.add(owner.name);
        }
        List<Field> fields = new ArrayList<>(owner.fields.size());
        for (FieldDefinition field : owner.fields) {
            fields.add(field(owner.name, field));
        }
        List<Field> result = implementing(owner, fields);
        if (isInterface) {
            resolving.remove(owner.name);
            interfaceFields.put(owner.name, result);
        }
        return result;
    }

    private List<Field> implementing(MergedType owner, List<Field> fields) {
        List<MergedType> parents = new ArrayList<>();
        for (String name : owner.implemented) {
            MergedType parent = schema.types.get(name);
            if (parent != null && parent.kind == Kind.INTERFACE && !resolving.contains(name)) {
                parents.add(parent);
            }
        }
        if (parents.isEmpty()) {
            return fields;
        }
        List<Field> result = new ArrayList<>(fields.size());
        for (Field field : fields) {
            List<Declared> declared = new ArrayList<>();
            for (MergedType parent : parents) {
                for (Field inherited : fields(parent)) {
                    if (inherited.sdlName().equals(field.sdlName())) {
                        declared.add(new Declared(parent.name, inherited));
                    }
                }
            }
            result.add(declared.isEmpty() ? field : implementing(owner, field, declared));
        }
        return result;
    }

    private Field implementing(MergedType owner, Field field, List<Declared> declared) {
        String coordinate = owner.name + "." + field.sdlName();
        Declared first = declared.get(0);
        for (Declared other : declared.subList(1, declared.size())) {
            if (!invariantShape(first.field().type(), false).equals(invariantShape(other.field().type(), false))
                    || primitive(first.field()) != primitive(other.field())) {
                error(Diagnostic.INTERFACE_FIELD_CONFLICT, "The field '" + coordinate + "' (" + field.origin() + ") implements the field '"
                        + first.owner() + "." + field.sdlName() + "' and the field '" + other.owner() + "." + field.sdlName()
                        + "', which differ in a list element type or in a primitive against a nullable scalar. No single Java type"
                        + " implements both. Give the two interface fields the same list element type and nullability.", field.origin());
                return field;
            }
        }
        TypeRef type = widen(field.type(), first.field().type(), false);
        if (type != field.type()) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING, Diagnostic.NARROWED_LIST_FIELD, "The field '" + coordinate + "' ("
                    + field.origin() + ") narrows the list element type '" + leafName(first.field().type()) + "' of the field '"
                    + first.owner() + "." + field.sdlName() + "' to '" + leafName(field.type()) + "'. Java generics are invariant, so"
                    + " the generated type keeps the list of the interface, with the element type '" + leafName(first.field().type())
                    + "'; the schema still returns the narrower objects.", null, field.origin()));
        }
        boolean boxed = declared.stream().anyMatch(it -> it.field().boxed() || it.field().type() instanceof TypeRef.Builtin builtin
                && builtin.nullable());
        boolean primitiveCapable = type instanceof TypeRef.Builtin builtin && !builtin.nullable() && isPrimitiveScalar(builtin);
        return new Field(field.sdlName(), field.name(), type, boxed && primitiveCapable, field.arguments(), field.docs(),
                field.directives(), field.origin());
    }

    private static boolean primitive(Field field) {
        return field.type() instanceof TypeRef.Builtin builtin && !builtin.nullable() && isPrimitiveScalar(builtin) && !field.boxed();
    }

    private static boolean isPrimitiveScalar(TypeRef.Builtin builtin) {
        return builtin.scalar() == BuiltinScalar.INT || builtin.scalar() == BuiltinScalar.FLOAT || builtin.scalar() == BuiltinScalar.BOOLEAN;
    }

    private static TypeRef widen(TypeRef own, TypeRef declared, boolean inList) {
        if (own instanceof TypeRef.ListOf ownList && declared instanceof TypeRef.ListOf declaredList) {
            TypeRef element = widen(ownList.element(), declaredList.element(), true);
            return element == ownList.element() ? own : new TypeRef.ListOf(element, ownList.nullable());
        }
        if (!inList || own instanceof TypeRef.ListOf || declared instanceof TypeRef.ListOf || leafName(own).equals(leafName(declared))) {
            return own;
        }
        boolean nullable = own.isNullable();
        if (declared instanceof TypeRef.Reference reference) {
            return new TypeRef.Reference(reference.target(), nullable);
        } else if (declared instanceof TypeRef.Mapped mapped) {
            return new TypeRef.Mapped(mapped.className(), nullable);
        }
        return own;
    }

    private static String invariantShape(TypeRef type, boolean inList) {
        if (type instanceof TypeRef.ListOf list) {
            return "[" + invariantShape(list.element(), true) + "]";
        }
        return inList ? leafName(type) : "";
    }

    private static String leafName(TypeRef type) {
        if (type instanceof TypeRef.ListOf list) {
            return leafName(list.element());
        } else if (type instanceof TypeRef.Reference reference) {
            return reference.target().sdlName();
        } else if (type instanceof TypeRef.Mapped mapped) {
            return mapped.className();
        }
        return ((TypeRef.Builtin) type).scalar().name();
    }

    private Field field(String owner, FieldDefinition field) {
        Origin origin = SchemaLoader.origin(field);
        checkField("field", owner, field.getName(), origin);
        String coordinate = owner + "." + field.getName();
        List<Argument> arguments = new ArrayList<>(field.getInputValueDefinitions().size());
        for (InputValueDefinition argument : field.getInputValueDefinitions()) {
            Origin argumentOrigin = SchemaLoader.origin(argument);
            checkReserved("argument", coordinate + "(" + argument.getName() + ")", argument.getName(), argumentOrigin);
            arguments.add(new Argument(argument.getName(), argument.getName(),
                    ref(argument.getType(), null, "argument '" + coordinate + "(" + argument.getName() + ")'", argumentOrigin),
                    NodeText.value(argument.getDefaultValue()),
                    text.docs(argument, argument.getDescription(), argument.getDirectives()),
                    text.directives(argument.getDirectives()), argumentOrigin));
        }
        return new Field(field.getName(), field.getName(), ref(field.getType(), coordinate, "field '" + coordinate + "'", origin), false,
                arguments, text.docs(field, field.getDescription(), field.getDirectives()), text.directives(field.getDirectives()),
                origin);
    }

    private List<ResolverContract> contracts() {
        List<ResolverContract> contracts = new ArrayList<>();
        for (Operation operation : Operation.values()) {
            MergedSchema.Root root = schema.roots.get(operation);
            MergedType type = root == null ? null : schema.types.get(root.typeName());
            if (type == null || type.kind != Kind.OBJECT) {
                continue;
            }
            for (FieldDefinition definition : type.fields) {
                Field field = field(type.name, definition);
                if (options.rootContracts()) {
                    contracts.add(contract(operation, type, field));
                }
            }
        }
        return contracts;
    }

    private ResolverContract contract(Operation operation, MergedType type, Field field) {
        Wrapper wrapper = operation == Operation.SUBSCRIPTION ? Wrapper.generic(options.subscriptionReturnType())
                : options.kotlinSuspend() ? Wrapper.SUSPEND : Wrapper.PLAIN;
        ContractMethod method = new ContractMethod(field.name(), new Coordinate(type.name, field.sdlName()), field.type(),
                field.arguments(), wrapper, field.docs(), field.directives(), field.origin());
        String simple = options.contractNameTemplate().replace("{Field}", capitalize(field.name()))
                .replace("{Operation}", type.name);
        Names contractNames = new Names(type.name + "." + field.sdlName(), options.effectiveResolverPackage(), simple);
        String description = "the resolver contract of the field '" + method.coordinate() + "' (" + field.origin() + ")";
        if (ROOT_TYPE_NAMES.contains(simple)) {
            error(Diagnostic.RESERVED_CONTRACT_NAME, "The option contractNameTemplate '" + options.contractNameTemplate() + "' names "
                    + description + " '" + simple + "', which is the name of a root type. Add text around the placeholders, for example"
                    + " {Field}{Operation}Resolver.", field.origin());
        }
        String problem = ReservedNames.typeNameProblem(options.language(), simple);
        if (problem != null) {
            error(Diagnostic.RESERVED_NAME, "The option contractNameTemplate '" + options.contractNameTemplate() + "' names " + description
                    + " '" + simple + "', which is a " + problem + ".", field.origin());
        }
        units.add(new Unit(contractNames.packageName(), simple, description, field.origin()));
        return new ResolverContract(operation, contractNames, MARKERS.get(operation), List.of(method), field.origin());
    }

    private static String capitalize(String name) {
        int first = name.codePointAt(0);
        return new StringBuilder().appendCodePoint(Character.toUpperCase(first)).append(name, Character.charCount(first), name.length())
                .toString();
    }

    private void register(MergedType type, String kindName) {
        Names generated = names.get(type.name);
        String problem = ReservedNames.typeNameProblem(options.language(), generated.simpleName());
        if (problem != null) {
            error(Diagnostic.RESERVED_NAME, "The " + kindName + " '" + type.name + "' (" + type.origin + ") cannot be generated as the class "
                    + generated.simpleName() + ": it is a " + problem + ". Rename it in the schema or set typeNamePrefix or typeNameSuffix.",
                    type.origin);
        }
        units.add(new Unit(generated.packageName(), generated.simpleName(), "the " + kindName + " '" + type.name + "' (" + type.origin + ")",
                type.origin));
    }

    private List<NameClass> nameClasses() {
        List<NameClass> classes = new ArrayList<>();
        if (options.nameConstants() == NameConstants.NONE) {
            return classes;
        }
        String namesPackage = options.effectiveNamesPackage();
        units.add(new Unit(namesPackage, TYPE_NAMES_CLASS, "the class of the type name constants", null));
        List<Pending> shared = new ArrayList<>();
        for (MergedType type : schema.types.values()) {
            if (!isRoot(type) && !names.containsKey(type.name)) {
                continue;
            }
            boolean fielded = type.kind == Kind.OBJECT || type.kind == Kind.INTERFACE || type.kind == Kind.INPUT;
            String typeDescription = "the type '" + type.name + "' (" + type.origin + ")";
            if (fielded && options.nameConstants() == NameConstants.FIELDS) {
                Names generated = names.get(type.name);
                String simple = (generated == null ? type.name : generated.simpleName()) + NAMES_SUFFIX;
                units.add(new Unit(namesPackage, simple, "the class of the name constants of " + typeDescription, type.origin));
                List<Pending> constants = new ArrayList<>();
                constants.add(new Pending(new NameConstant(TYPE_CONSTANT, type.name, type.origin), typeDescription));
                if (type.kind == Kind.INPUT) {
                    for (InputValueDefinition field : type.inputFields) {
                        constants.add(fieldConstant(type, field.getName(), SchemaLoader.origin(field)));
                    }
                } else {
                    for (FieldDefinition field : type.fields) {
                        constants.add(fieldConstant(type, field.getName(), SchemaLoader.origin(field)));
                    }
                }
                addClass(classes, namesPackage, simple, constants);
            } else {
                shared.add(new Pending(new NameConstant(constantName(type.name), type.name, type.origin), typeDescription));
            }
        }
        if (!shared.isEmpty()) {
            addClass(classes, namesPackage, TYPE_NAMES_CLASS, shared);
        }
        return classes;
    }

    private static Pending fieldConstant(MergedType owner, String field, Origin origin) {
        return new Pending(new NameConstant(constantName(field), field, origin),
                "the field '" + owner.name + "." + field + "' (" + origin + ")");
    }

    private void addClass(List<NameClass> classes, String packageName, String simpleName, List<Pending> pending) {
        Map<String, Pending> seen = new HashMap<>();
        List<NameConstant> constants = new ArrayList<>(pending.size());
        for (Pending item : pending) {
            Pending first = seen.putIfAbsent(item.constant().name(), item);
            if (first != null) {
                error(Diagnostic.NAME_COLLISION, "Two schema elements generate the constant " + item.constant().name() + " in the class "
                        + packageName + "." + simpleName + ": " + first.description() + " and " + item.description() + ". Rename one of"
                        + " them in the schema.", item.constant().origin());
            }
            constants.add(item.constant());
        }
        classes.add(new NameClass(packageName, simpleName, constants));
    }

    /**
     * The identifier of the constant of a schema name: the name in upper case, with an underscore before each word, so that
     * {@code fooBar} gives {@code FOO_BAR} and {@code HTTPStatus} gives {@code HTTP_STATUS}. GraphQL names are ASCII, so the
     * case conversion does not depend on the locale.
     *
     * @param name the schema name
     * @return the identifier
     */
    static String constantName(String name) {
        StringBuilder out = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                char previous = name.charAt(i - 1);
                boolean startsWord = i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1));
                if (Character.isLowerCase(previous) || Character.isDigit(previous) || Character.isUpperCase(previous) && startsWord) {
                    out.append('_');
                }
            }
            out.append(Character.toUpperCase(c));
        }
        return out.toString();
    }

    private void collisions() {
        Map<String, Unit> seen = new HashMap<>();
        for (Unit unit : units) {
            Unit first = seen.putIfAbsent(unit.folded(), unit);
            if (first == null) {
                continue;
            }
            String message = first.qualified().equals(unit.qualified())
                    ? "Two schema elements generate the class " + unit.qualified() + ": " + first.description() + " and "
                    + unit.description() + ". Rename one of them in the schema, or set typeNamePrefix, typeNameSuffix or a package option."
                    : "Two schema elements generate classes whose names differ only in case, so they overwrite each other on a"
                    + " case-insensitive file system: " + first.description() + " generates " + first.qualified() + " and "
                    + unit.description() + " generates " + unit.qualified()
                    + ". Rename one of them in the schema, or set typeNamePrefix, typeNameSuffix or a package option.";
            error(Diagnostic.NAME_COLLISION, message, unit.origin() != null ? unit.origin() : first.origin());
        }
    }

    private void checkField(String what, String owner, String name, Origin origin) {
        checkReserved(what, owner + "." + name, name, origin);
        if (OBJECT_METHODS.contains(name)) {
            error(Diagnostic.OBJECT_METHOD_NAME, "The " + what + " '" + owner + "." + name + "' (" + origin + ") clashes with a method of"
                    + " java.lang.Object: a record component cannot be named " + name + ". Rename it in the schema; the generator has no"
                    + " renaming option yet.", origin);
        }
    }

    private void checkReserved(String what, String element, String name, Origin origin) {
        String language = ReservedNames.languageOf(options.language(), name);
        if (language != null) {
            error(Diagnostic.RESERVED_NAME, "The " + what + " '" + element + "' (" + origin + ") cannot be generated: " + name + " is a "
                    + language + " keyword. Rename it in the schema; the generator has no renaming option yet.", origin);
        }
    }

    private TypeRef ref(Type<?> type, String mappingKey, String where, Origin origin) {
        return ref(type, true, mappingKey, where, origin);
    }

    private TypeRef ref(Type<?> type, boolean nullable, String mappingKey, String where, Origin origin) {
        if (type instanceof NonNullType nonNull) {
            return ref(nonNull.getType(), false, mappingKey, where, origin);
        } else if (type instanceof ListType list) {
            return new TypeRef.ListOf(ref(list.getType(), true, mappingKey, where, origin), nullable);
        }
        String name = ((TypeName) type).getName();
        String mapped = mappingKey != null ? options.typeMappings().get(mappingKey) : null;
        if (mapped != null) {
            usedMappings.add(mappingKey);
        } else {
            mapped = mapping(name);
            if (mapped != null) {
                usedMappings.add(name);
            }
        }
        if (mapped != null) {
            return new TypeRef.Mapped(mapped, nullable);
        }
        BuiltinScalar builtin = BUILTIN.get(name);
        if (builtin != null) {
            return new TypeRef.Builtin(builtin, nullable);
        }
        MergedType target = schema.types.get(name);
        if (target == null) {
            error(Diagnostic.UNKNOWN_TYPE, "The type '" + name + "' of the " + where + " (" + origin + ") is not defined in the schema files and"
                    + " has no entry in the option typeMappings.", origin);
            return new TypeRef.Reference(new Names(name, options.effectiveModelPackage(), name), nullable);
        }
        if (isRoot(target)) {
            error(Diagnostic.ROOT_TYPE_REFERENCE, "The " + where + " (" + origin + ") has the root type '" + name + "' as its type, and"
                    + " the generator makes no model of a root type. Map it with the option typeMappings." + name + ".", origin);
        }
        Names names = this.names.get(name);
        return new TypeRef.Reference(names != null ? names : new Names(name, options.effectiveModelPackage(), name), nullable);
    }

    private record Declared(String owner, Field field) {
    }

    private record Pending(NameConstant constant, String description) {
    }

    private record Unit(String packageName, String simpleName, String description, Origin origin) {
        String qualified() {
            return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        }

        String folded() {
            return qualified().toLowerCase(Locale.ROOT);
        }
    }
}
