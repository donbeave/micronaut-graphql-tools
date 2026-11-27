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

import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.client.ClientDocuments;
import io.micronaut.graphql.tools.binding.internal.client.ClientOperation;
import io.micronaut.graphql.tools.binding.internal.finding.DocumentLocation;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.model.TypeModel;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rules of the parameters of an operation method against the variables of its operation. Every parameter binds a variable by
 * its name or by {@code @GraphQLVariable}, or is a header or a cookie; every variable that is non-null and has no default needs
 * a parameter; and the declared type of a parameter fits the type of its variable. The rules of the names need no schema and run
 * whatever the checks say, because the runtime refuses such a method at its first call; the rules of the types run with the
 * schema.
 */
@Internal
public final class ClientVariableRules {

    private ClientVariableRules() {
    }

    /**
     * @param method    the operation method
     * @param operation the operation that the method runs
     * @param model     the types of the parameters
     * @param scalars   the custom scalars that the client has mapped so far
     * @return the findings, in the order of the parameters and then of the variables
     */
    public static List<Finding> check(ClientMethod method, ClientOperation operation, TypeModel model, ClientScalars scalars) {
        List<Finding> findings = new ArrayList<>();
        Map<String, ClientParameter> bound = new LinkedHashMap<>();
        for (ClientParameter parameter : method.parameters()) {
            switch (parameter.kind()) {
                case VARIABLE -> bind(method, operation, parameter, bound, findings);
                case OTHER -> findings.add(unbound(method, "the parameter " + parameter.name() + " is bound by an HTTP annotation, "
                        + "and an operation method takes the variables of its operation and @Header and @CookieValue parameters only",
                        "remove the annotation to bind the variable with that name, or bind a header with @Header or a cookie with "
                        + "@CookieValue"));
                default -> {
                }
            }
        }
        GraphQLSchema schema = operation.schema();
        if (schema == null) {
            return findings;
        }
        VariableTypes types = new VariableTypes(model, scalars, method.member(), findings);
        for (VariableDefinition variable : operation.definition().getVariableDefinitions()) {
            ClientParameter parameter = bound.get(variable.getName());
            DocumentLocation at = ClientDocuments.location(variable.getSourceLocation());
            if (parameter == null) {
                if (variable.getType() instanceof NonNullType && variable.getDefaultValue() == null) {
                    findings.add(new Finding(BindingCode.CLIENT_VARIABLE_MISSING.getCode(), method.member(), "the operation declares the "
                            + "variable $" + variable.getName() + ": " + SchemaTypes.print(variable.getType()) + ", which is non-null and "
                            + "has no default, and no parameter binds it", null, null, null, "add a parameter named " + variable.getName()
                            + " (or annotated with @GraphQLVariable(\"" + variable.getName() + "\")), give the variable a default, or "
                            + "make it nullable", at));
                }
                continue;
            }
            GraphQLType type = resolve(schema, variable.getType());
            if (type != null) {
                types.match(parameter.type(), type, parameter.name(), at);
            }
        }
        return findings;
    }

    private static void bind(ClientMethod method, ClientOperation operation, ClientParameter parameter,
                             Map<String, ClientParameter> bound, List<Finding> findings) {
        String name = parameter.variableName();
        if (!operation.variables().contains(name)) {
            findings.add(unbound(method, "the parameter " + parameter.name() + " binds the variable $" + name + ", which the operation "
                    + "does not declare" + (operation.variables().isEmpty() ? " (it declares none)" : " (it declares "
                    + operation.variables().stream().map(it -> "$" + it).toList() + ")"),
                    "name the variable with @GraphQLVariable, mark the parameter @Header or @CookieValue, or remove it"));
        } else if (bound.putIfAbsent(name, parameter) != null) {
            findings.add(unbound(method, "the parameters " + bound.get(name).name() + " and " + parameter.name() + " both bind the "
                    + "variable $" + name, "bind the variable with one parameter"));
        }
    }

    private static Finding unbound(ClientMethod method, String sentence, String fix) {
        return new Finding(BindingCode.CLIENT_PARAMETER_UNBOUND.getCode(), method.member(), sentence, null, null, null, fix);
    }

    // the type of a variable definition in the schema, null when the schema has no such type
    private static @Nullable GraphQLType resolve(GraphQLSchema schema, Type<?> type) {
        if (type instanceof NonNullType nonNull) {
            GraphQLType inner = resolve(schema, nonNull.getType());
            return inner == null ? null : GraphQLNonNull.nonNull(inner);
        }
        if (type instanceof ListType list) {
            GraphQLType inner = resolve(schema, list.getType());
            return inner == null ? null : GraphQLList.list(inner);
        }
        return type instanceof TypeName name ? schema.getType(name.getName()) : null;
    }
}
