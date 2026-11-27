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
package io.micronaut.graphql.tools.binding.internal.client.method;

import graphql.language.SelectionSet;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.client.ClientDocuments;
import io.micronaut.graphql.tools.binding.internal.client.ClientTypenames;
import io.micronaut.graphql.tools.binding.internal.finding.DocumentLocation;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.model.AnnotationView;
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.model.JEnumConstant;
import io.micronaut.graphql.tools.binding.internal.model.JProperty;
import io.micronaut.graphql.tools.binding.internal.model.Nullability;
import io.micronaut.graphql.tools.binding.internal.model.TypeModel;
import io.micronaut.graphql.tools.binding.internal.model.TypeRef;
import io.micronaut.graphql.tools.binding.internal.schema.ScalarCatalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares the declared result type of an operation with what the selection selects, level by level: every selected field has a
 * property, the nullability and the lists agree, scalars go through the catalog, enums have a constant for each value, and an
 * interface or union selection is a polymorphic type with a branch for each object type that the selection names and a default
 * branch for the others. The Java type is what the client deserializes into, so a position is judged by what the deserializer can
 * read from the response.
 */
@Internal
final class ResultTypes {

    static final String JSON_TYPE_INFO = "com.fasterxml.jackson.annotation.JsonTypeInfo";
    static final String JSON_SUB_TYPES = "com.fasterxml.jackson.annotation.JsonSubTypes";
    static final String JSON_TYPE_NAME = "com.fasterxml.jackson.annotation.JsonTypeName";
    static final String JSON_ENUM_DEFAULT = "com.fasterxml.jackson.annotation.JsonEnumDefaultValue";
    static final String TYPENAME = ClientTypenames.TYPENAME;

    private static final Set<String> NO_DEFAULT = Set.of("java.lang.Void", "com.fasterxml.jackson.annotation.JsonTypeInfo$None");

    private final TypeModel model;
    private final ClientScalars scalars;
    private final ClientSelections selections;
    private final String member;
    private final List<Finding> findings;

    ResultTypes(TypeModel model, ClientScalars scalars, ClientSelections selections, String member, List<Finding> findings) {
        this.model = model;
        this.scalars = scalars;
        this.selections = selections;
        this.member = member;
        this.findings = findings;
    }

    /**
     * @param declared the declared type of the result, or of a property of the class that it declares
     * @param type     the schema type at the position
     * @param fields   the fields that select the position, one per occurrence of its response key; the first has the type
     * @param path     what the position is called in a message, such as {@code book.author}
     * @param absent   whether the position can be absent from the response although its type is non-null
     */
    void match(TypeRef declared, GraphQLType type, List<ClientSelections.SelectedField> fields, String path, boolean absent) {
        boolean nonNull = type instanceof GraphQLNonNull && !absent;
        GraphQLType inner = type instanceof GraphQLNonNull wrapped ? wrapped.getWrappedType() : type;
        DocumentLocation at = location(fields);
        TypeRef value = declared;
        boolean optional = JavaTypes.isOptional(declared);
        if (optional) {
            value = JavaTypes.held(declared);
        }
        if (!nonNull && !optional && (declared.primitive() || declared.kotlin() && declared.nullability() == Nullability.NON_NULL)) {
            add(at, "the type of " + describe(path) + " is " + (declared.primitive() ? "the primitive " : "non-null ")
                    + JavaTypes.simple(declared.rawName()) + ", and the " + (absent && type instanceof GraphQLNonNull
                    ? "field can be left out of the response by @skip or @include" : "GraphQL type " + GraphQLTypeUtil.simplePrint(type)
                    + " is nullable") + ", so a null cannot be read",
                    declared.primitive() ? "declare the wrapper type (" + JavaTypes.simple(JavaTypes.box(declared.rawName()))
                            + ") or an Optional, or make the schema type non-null" : "declare the type nullable");
        }
        if (value == null || JavaTypes.OBJECT.equals(value.rawName()) || isMap(value)) {
            return;
        }
        if (inner instanceof GraphQLList list) {
            matchList(value, list, fields, path, absent);
        } else if (inner instanceof GraphQLScalarType scalar) {
            matchScalar(value, scalar, path, at);
        } else if (inner instanceof GraphQLEnumType enumeration) {
            matchEnum(value, enumeration, path, at);
        } else if (inner instanceof GraphQLObjectType object) {
            matchObject(value, object, selections.collect(sets(fields), object).common, path, at);
        } else if (inner instanceof GraphQLInterfaceType || inner instanceof GraphQLUnionType) {
            matchAbstract(value, inner, selections.collect(sets(fields), inner), path, at);
        }
    }

    /**
     * @param declared the declared type, which must be a class
     * @param type     the object type
     * @param byKey    the selected fields by response key
     * @param path     the name of the position
     * @param at       where the selection is
     */
    void matchObject(TypeRef declared, GraphQLFieldsContainer type, Map<String, List<ClientSelections.SelectedField>> byKey,
                     String path, @Nullable DocumentLocation at) {
        JClass target = resolve(declared);
        if (target == null || JavaTypes.OBJECT.equals(declared.rawName()) || target.isAssignableTo(JavaTypes.MAP)) {
            return;
        }
        if (declared.primitive() || target.kind() == JClass.Kind.ENUM || declared.rawName().startsWith("java.") || target.kind() == JClass.Kind.INTERFACE) {
            add(at, "the type of " + describe(path) + " is " + JavaTypes.simple(declared.rawName()) + ", and the GraphQL type is the object "
                    + "type " + type.getName() + ", whose selected fields are read into the properties of a class",
                    "declare a class, a record or a data class with a property for each selected field, or a Map");
            return;
        }
        Map<String, JProperty> properties = new HashMap<>();
        for (JProperty property : target.writableProperties()) {
            if (!property.annotations().has(VariableTypes.JSON_IGNORE)) {
                properties.put(property.annotations().stringValue(VariableTypes.JSON_PROPERTY, "value").filter(it -> !it.isEmpty())
                        .orElse(property.name()), property);
            }
        }
        for (Map.Entry<String, List<ClientSelections.SelectedField>> entry : byKey.entrySet()) {
            String key = entry.getKey();
            List<ClientSelections.SelectedField> fields = entry.getValue();
            if (TYPENAME.equals(fields.get(0).field().getName())) {
                continue;
            }
            JProperty property = properties.get(key);
            if (property == null) {
                add(ClientDocuments.location(fields.get(0).field().getSourceLocation()), "the selection of " + describe(path) + " includes the field " + key
                        + ", which " + target.name() + " has no settable property for (the response key is the alias, or the name of "
                        + "the field)", "add a property " + key + " to " + JavaTypes.simple(target.name()) + ", name it with "
                        + "@JsonProperty, or remove the field from the selection");
                continue;
            }
            GraphQLFieldDefinition definition = definition(fields.get(0));
            if (definition != null) {
                match(property.type(), definition.getType(), fields, child(path, key), absent(fields));
            }
        }
    }

    /**
     * @param declared the declared type of the payload
     * @param key      the response key of the root field
     * @param fields   the occurrences of the root field in the operation
     */
    void matchField(TypeRef declared, String key, List<ClientSelections.SelectedField> fields) {
        GraphQLFieldDefinition definition = definition(fields.get(0));
        if (definition != null) {
            match(declared, definition.getType(), fields, key, absent(fields));
        }
    }

    private void matchList(TypeRef value, GraphQLList list, List<ClientSelections.SelectedField> fields, String path, boolean absent) {
        TypeRef element;
        DocumentLocation at = location(fields);
        if (value.array()) {
            element = new TypeRef(value.rawName(), value.typeArguments(), Nullability.UNKNOWN, value.primitive(), false, value.kotlin());
        } else {
            JClass declared = resolve(value);
            if (declared == null || !declared.isAssignableTo(JavaTypes.ITERABLE)) {
                add(at, "the type of " + describe(path) + " is " + JavaTypes.simple(value.rawName()) + ", and the GraphQL type "
                        + GraphQLTypeUtil.simplePrint(list) + " is a list", "declare a List, a Set, another Collection or an array");
                return;
            }
            element = value.typeArguments().isEmpty() ? null : value.typeArguments().get(0);
        }
        if (element != null) {
            match(element, list.getWrappedType(), fields, path + "[]", absent);
        }
    }

    private void matchScalar(TypeRef value, GraphQLScalarType scalar, String path, @Nullable DocumentLocation at) {
        String javaType = JavaTypes.box(value.rawName());
        String name = scalar.getName();
        if (!ScalarCatalog.isKnown(name)) {
            Finding conflict = scalars.map(name, javaType, member, "the type of " + describe(path), at);
            if (conflict != null) {
                findings.add(conflict);
            }
            return;
        }
        if (!ScalarCatalog.acceptsClassName(name, javaType)) {
            add(at, "the type of " + describe(path) + " is " + JavaTypes.simple(value.rawName()) + ", and the GraphQL type is the scalar "
                    + name + ", which the catalog maps to " + ScalarCatalog.acceptedClassNames(name),
                    "declare " + ScalarCatalog.acceptedClassNames(name) + ", or change the type of the field");
        }
    }

    private void matchEnum(TypeRef value, GraphQLEnumType enumeration, String path, @Nullable DocumentLocation at) {
        if (JavaTypes.box(value.rawName()).equals("java.lang.String")) {
            return;
        }
        JClass declared = resolve(value);
        if (declared == null) {
            return;
        }
        if (declared.kind() != JClass.Kind.ENUM) {
            add(at, "the type of " + describe(path) + " is " + JavaTypes.simple(value.rawName()) + ", and the GraphQL type is the enum "
                    + enumeration.getName(), "declare a Java enum with the values of " + enumeration.getName() + ", or a String");
            return;
        }
        Set<String> constants = new TreeSet<>();
        boolean fallback = false;
        for (JEnumConstant constant : declared.enumConstants()) {
            constants.add(constant.annotations().stringValue(VariableTypes.JSON_PROPERTY, "value").filter(it -> !it.isEmpty())
                    .orElse(constant.name()));
            fallback |= constant.annotations().has(JSON_ENUM_DEFAULT);
        }
        List<String> missing = new ArrayList<>();
        for (GraphQLEnumValueDefinition definition : enumeration.getValues()) {
            if (!constants.contains(definition.getName())) {
                missing.add(definition.getName());
            }
        }
        if (!missing.isEmpty() && !fallback) {
            add(at, "the type of " + describe(path) + " is the enum " + JavaTypes.simple(value.rawName()) + ", which has no constant for "
                    + missing + " of the GraphQL enum " + enumeration.getName() + ", so a response with one of them cannot be read",
                    "add the constants, mark one @JsonEnumDefaultValue to read the others as it, or declare a String");
        }
    }

    private void matchAbstract(TypeRef value, GraphQLType type, ClientSelections.Branches branches, String path, @Nullable DocumentLocation at) {
        JClass target = resolve(value);
        if (target == null) {
            return;
        }
        boolean polymorphic = target.kind() == JClass.Kind.INTERFACE || target.kind() == JClass.Kind.ABSTRACT_CLASS;
        if (branches.specific.isEmpty() && !polymorphic && type instanceof GraphQLFieldsContainer common) {
            // nothing but the fields that every type has: one class reads them, and the type name is ignored
            matchObject(value, common, branches.common, path, at);
            return;
        }
        polymorphism(value, target, type, branches, path, at);
    }

    // the type must read the type name into a subtype: a polymorphic type with a branch for each object type that the selection names
    // and a default branch for the others
    private void polymorphism(TypeRef value, JClass target, GraphQLType type, ClientSelections.Branches branches, String path,
                              @Nullable DocumentLocation at) {
        AnnotationView annotations = target.annotations();
        boolean polymorphic = (target.kind() == JClass.Kind.INTERFACE || target.kind() == JClass.Kind.ABSTRACT_CLASS)
                && annotations.has(JSON_TYPE_INFO);
        String abstractName = ((graphql.schema.GraphQLNamedType) type).getName();
        if (!polymorphic) {
            add(at, "the type of " + describe(path) + " is " + JavaTypes.simple(value.rawName()) + ", and the GraphQL type is the "
                    + (type instanceof GraphQLUnionType ? "union " : "interface ") + abstractName + " selected as one of several object "
                    + "types, which needs a sealed interface or an abstract class annotated with @JsonTypeInfo",
                    "declare a sealed interface with @JsonTypeInfo(use = NAME, property = \"__typename\", defaultImpl = Other.class) "
                    + "and @JsonSubTypes, one record per selected object type, and a record for the default branch");
            return;
        }
        String property = annotations.stringValue(JSON_TYPE_INFO, "property").orElse("");
        String use = annotations.stringValue(JSON_TYPE_INFO, "use").orElse("");
        if (!property.equals(TYPENAME) || !use.endsWith("NAME")) {
            add(at, "the type of " + describe(path) + " is " + JavaTypes.simple(value.rawName()) + ", whose @JsonTypeInfo reads "
                    + (property.isEmpty() ? "no property" : "the property " + property) + (use.endsWith("NAME") ? "" : " with use " + use)
                    + ", and the client tells the object types apart by the response field __typename",
                    "use @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = \"__typename\", defaultImpl = Other.class)");
            return;
        }
        List<String> defaults = annotations.classValues(JSON_TYPE_INFO, "defaultImpl").stream().filter(it -> !NO_DEFAULT.contains(it)).toList();
        if (defaults.isEmpty()) {
            add(at, "the type of " + describe(path) + " is " + JavaTypes.simple(value.rawName()) + ", whose @JsonTypeInfo has no defaultImpl, "
                    + "so a response with an object type that the client does not know cannot be read",
                    "add a record for the other types and name it with defaultImpl");
        }
        Map<String, String> subtypes = new LinkedHashMap<>();
        for (AnnotationView.NamedClass subtype : annotations.namedClasses(JSON_SUB_TYPES, "value", "value", "name")) {
            subtypes.put(subtype.name().isEmpty() ? typeName(subtype.className()) : subtype.name(), subtype.className());
        }
        for (Map.Entry<String, Map<String, List<ClientSelections.SelectedField>>> branch : branches.specific.entrySet()) {
            String objectName = branch.getKey();
            String subtype = subtypes.get(objectName);
            if (subtype == null) {
                add(at, "the type of " + describe(path) + " is " + JavaTypes.simple(value.rawName()) + ", which has no subtype named "
                        + objectName + ", an object type that the selection names", "add a subtype for " + objectName + " to @JsonSubTypes, or "
                        + "remove its fragment from the selection");
                continue;
            }
            GraphQLType object = selections.type(objectName);
            if (object instanceof GraphQLObjectType objectType) {
                matchObject(TypeRef.of(subtype), objectType, branches.forType(objectName), path + "(" + objectName + ")", at);
            }
        }
        if (!defaults.isEmpty() && type instanceof GraphQLFieldsContainer container) {
            matchObject(TypeRef.of(defaults.get(0)), container, branches.common, path + "(other)", at);
        }
    }

    // the name that Jackson gives a subtype that names none: its @JsonTypeName, or its simple class name
    private String typeName(String className) {
        JClass subtype = resolve(TypeRef.of(className));
        String named = subtype == null ? "" : subtype.annotations().stringValue(JSON_TYPE_NAME, "value").orElse("");
        return named.isEmpty() ? JavaTypes.simple(className) : named;
    }

    private @Nullable GraphQLFieldDefinition definition(ClientSelections.SelectedField selected) {
        return selected.scope() == null ? null : selected.scope().getFieldDefinition(selected.field().getName());
    }

    private static boolean absent(List<ClientSelections.SelectedField> fields) {
        return fields.stream().allMatch(ClientSelections.SelectedField::conditional);
    }

    private static List<SelectionSet> sets(List<ClientSelections.SelectedField> fields) {
        List<SelectionSet> sets = new ArrayList<>();
        for (ClientSelections.SelectedField selected : fields) {
            if (selected.field().getSelectionSet() != null) {
                sets.add(selected.field().getSelectionSet());
            }
        }
        return sets;
    }

    private static @Nullable DocumentLocation location(List<ClientSelections.SelectedField> fields) {
        return fields.isEmpty() ? null : ClientDocuments.location(fields.get(0).field().getSourceLocation());
    }

    private boolean isMap(TypeRef value) {
        JClass declared = value.array() || value.primitive() ? null : resolve(value);
        return declared != null && declared.isAssignableTo(JavaTypes.MAP);
    }

    private @Nullable JClass resolve(TypeRef value) {
        try {
            return model.resolve(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String child(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    private static String describe(String path) {
        return path.isEmpty() ? "the result" : path;
    }

    private void add(@Nullable DocumentLocation location, String sentence, String fix) {
        findings.add(new Finding(BindingCode.CLIENT_RESULT_SHAPE.getCode(), member, sentence, null, null, null, fix, location));
    }
}
