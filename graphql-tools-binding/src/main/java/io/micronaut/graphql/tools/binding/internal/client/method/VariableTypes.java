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

import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.DocumentLocation;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.model.JEnumConstant;
import io.micronaut.graphql.tools.binding.internal.model.JProperty;
import io.micronaut.graphql.tools.binding.internal.model.Nullability;
import io.micronaut.graphql.tools.binding.internal.model.TypeModel;
import io.micronaut.graphql.tools.binding.internal.model.TypeRef;
import io.micronaut.graphql.tools.binding.internal.schema.ScalarCatalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares the declared type of a parameter with the type of the variable it binds, level by level: nullability, lists, scalars
 * through the catalog, enums and input objects. The Java type is what the client serializes, so a position is judged by what the
 * serializer can write and what the server accepts there.
 */
@Internal
final class VariableTypes {

    static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    static final String JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";

    private final TypeModel model;
    private final ClientScalars scalars;
    private final String member;
    private final List<Finding> findings;
    private final Set<String> inProgress = new HashSet<>();

    VariableTypes(TypeModel model, ClientScalars scalars, String member, List<Finding> findings) {
        this.model = model;
        this.scalars = scalars;
        this.member = member;
        this.findings = findings;
    }

    /**
     * @param declared the declared type of the parameter, or of a property of the class that it declares
     * @param type     the type of the variable, or of the input field that the property is serialized as
     * @param path     what the position is called in a message, such as {@code title} or {@code filter.tags}
     * @param location where the document declares the variable
     */
    void match(TypeRef declared, GraphQLType type, String path, @Nullable DocumentLocation location) {
        boolean nonNull = type instanceof GraphQLNonNull;
        GraphQLType inner = nonNull ? ((GraphQLNonNull) type).getWrappedType() : type;
        TypeRef value = declared;
        boolean optional = JavaTypes.isOptional(declared);
        if (optional) {
            value = JavaTypes.held(declared);
        }
        if (nonNull && (optional || JavaTypes.mayBeNull(declared))) {
            add(location, "the " + describe(path) + " is declared " + (optional ? "an Optional" : "nullable") + ", and the variable "
                    + "type " + GraphQLTypeUtil.simplePrint(type) + " is non-null, so a null could be sent",
                    optional ? "declare the type itself instead of an Optional, or make the variable nullable (remove the '!')"
                            : "declare the type non-null, or make the variable nullable (remove the '!')");
        }
        if (value == null || JavaTypes.OBJECT.equals(value.rawName()) || isMap(value)) {
            return;
        }
        if (inner instanceof GraphQLList list) {
            matchList(value, list, path, location);
        } else if (inner instanceof GraphQLScalarType scalar) {
            matchScalar(value, scalar, path, location);
        } else if (inner instanceof GraphQLEnumType enumeration) {
            matchEnum(value, enumeration, path, location);
        } else if (inner instanceof GraphQLInputObjectType input) {
            matchInput(value, input, path, location);
        }
    }

    private void matchList(TypeRef value, GraphQLList list, String path, @Nullable DocumentLocation location) {
        TypeRef element;
        if (value.array()) {
            element = new TypeRef(value.rawName(), value.typeArguments(), Nullability.UNKNOWN, value.primitive(), false, value.kotlin());
        } else {
            JClass declared = resolve(value);
            if (declared == null || !declared.isAssignableTo(JavaTypes.ITERABLE)) {
                add(location, "the " + describe(path) + " is " + JavaTypes.simple(value.rawName()) + ", and the variable type "
                        + GraphQLTypeUtil.simplePrint(list) + " is a list", "declare a List, a Set, another Collection or an array");
                return;
            }
            element = value.typeArguments().isEmpty() ? null : value.typeArguments().get(0);
        }
        if (element != null) {
            match(element, list.getWrappedType(), path + "[]", location);
        }
    }

    private void matchScalar(TypeRef value, GraphQLScalarType scalar, String path, @Nullable DocumentLocation location) {
        String javaType = JavaTypes.box(value.rawName());
        String name = scalar.getName();
        if (!ScalarCatalog.isKnown(name)) {
            Finding conflict = scalars.map(name, javaType, member, "the " + describe(path), location);
            if (conflict != null) {
                findings.add(conflict);
            }
            return;
        }
        if (!ScalarCatalog.acceptsClassName(name, javaType)) {
            add(location, "the " + describe(path) + " is " + JavaTypes.simple(value.rawName()) + ", and the variable type is the scalar "
                    + name + ", which the catalog maps to " + ScalarCatalog.acceptedClassNames(name),
                    "declare " + ScalarCatalog.acceptedClassNames(name) + " for the parameter, or change the type of the variable");
        }
    }

    private void matchEnum(TypeRef value, GraphQLEnumType enumeration, String path, @Nullable DocumentLocation location) {
        if (JavaTypes.box(value.rawName()).equals("java.lang.String")) {
            return;
        }
        JClass declared = resolve(value);
        if (declared == null) {
            return;
        }
        if (declared.kind() != JClass.Kind.ENUM) {
            add(location, "the " + describe(path) + " is " + JavaTypes.simple(value.rawName()) + ", and the variable type is the enum "
                    + enumeration.getName(), "declare a Java enum with the values of " + enumeration.getName() + ", or a String");
            return;
        }
        Set<String> values = new TreeSet<>();
        for (GraphQLEnumValueDefinition definition : enumeration.getValues()) {
            values.add(definition.getName());
        }
        List<String> unknown = new ArrayList<>();
        for (JEnumConstant constant : declared.enumConstants()) {
            String name = constant.annotations().stringValue(JSON_PROPERTY, "value").filter(it -> !it.isEmpty()).orElse(constant.name());
            if (!values.contains(name)) {
                unknown.add(constant.name());
            }
        }
        if (!unknown.isEmpty()) {
            add(location, "the " + describe(path) + " is the enum " + JavaTypes.simple(value.rawName()) + ", whose constants "
                    + unknown + " are not values of " + enumeration.getName() + " " + values + ", so the server rejects them",
                    "remove the constants, or add the values to the schema");
        }
    }

    private void matchInput(TypeRef value, GraphQLInputObjectType input, String path, @Nullable DocumentLocation location) {
        JClass declared = resolve(value);
        if (declared == null) {
            return;
        }
        if (declared.kind() == JClass.Kind.ENUM || value.rawName().startsWith("java.") || value.primitive()) {
            add(location, "the " + describe(path) + " is " + JavaTypes.simple(value.rawName()) + ", and the variable type is the input "
                    + "type " + input.getName(), "declare a class, a record or a Map whose properties are the fields of "
                    + input.getName());
            return;
        }
        String key = declared.name() + "/" + input.getName();
        if (!inProgress.add(key)) {
            return;
        }
        try {
            Map<String, JProperty> properties = new LinkedHashMap<>();
            for (JProperty property : declared.readableProperties()) {
                if (!property.annotations().has(JSON_IGNORE)) {
                    properties.put(property.annotations().stringValue(JSON_PROPERTY, "value").filter(it -> !it.isEmpty())
                            .orElse(property.name()), property);
                }
            }
            for (GraphQLInputObjectField field : input.getFieldDefinitions()) {
                JProperty property = properties.remove(field.getName());
                if (property == null) {
                    boolean required = field.getType() instanceof GraphQLNonNull && !field.hasSetDefaultValue();
                    if (required) {
                        add(location, "the " + describe(path) + " is " + JavaTypes.simple(value.rawName()) + ", which has no property "
                                + "for the required field " + field.getName() + " of the input type " + input.getName(),
                                "add a property " + field.getName() + " to the class, or make the field nullable");
                    }
                } else {
                    match(property.type(), field.getType(), path + "." + field.getName(), location);
                }
            }
            for (String extra : properties.keySet()) {
                add(location, "the " + describe(path) + " is " + JavaTypes.simple(value.rawName()) + ", whose property " + extra
                        + " is not a field of the input type " + input.getName() + ", so the server rejects the value",
                        "remove the property, mark it @JsonIgnore, or add the field to the schema");
            }
        } finally {
            inProgress.remove(key);
        }
    }

    private boolean isMap(TypeRef value) {
        JClass declared = value.array() || value.primitive() ? null : resolve(value);
        return declared != null && declared.isAssignableTo(JavaTypes.MAP);
    }

    // a type that the model does not know (an array of arrays) is not judged
    private @Nullable JClass resolve(TypeRef value) {
        try {
            return model.resolve(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String describe(String path) {
        return "parameter " + path;
    }

    private void add(@Nullable DocumentLocation location, String sentence, String fix) {
        findings.add(new Finding(BindingCode.CLIENT_VARIABLE_TYPE.getCode(), member, sentence, null, null, null, fix, location));
    }
}
