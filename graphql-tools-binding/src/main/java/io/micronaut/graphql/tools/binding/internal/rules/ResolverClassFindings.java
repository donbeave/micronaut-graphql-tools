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
package io.micronaut.graphql.tools.binding.internal.rules;

import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SourceLocation;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.finding.SchemaLocation;
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames;
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.model.JMethod;
import io.micronaut.graphql.tools.binding.internal.model.TypeModel;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.CountMismatch;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.DuplicateParameter;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.InvalidSource;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.ParameterWithoutArgument;
import io.micronaut.graphql.tools.binding.internal.rules.ClassVerdict.FieldVerdict;
import io.micronaut.graphql.tools.binding.internal.rules.ClassVerdict.MethodVerdict;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaCoordinates;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaCoordinates.ResolverKind;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The findings of the per-class rules for one concrete resolver class: what the compile-time check reports, in the words
 * and with the codes the startup uses. It reports only what the class and the schema decide. Whether a field has any
 * method, how the fields of the other types are mapped and whether the class is an enabled bean are whole-program facts
 * that it never reads.
 *
 * <p>A violation of a method with a declared coordinate is reported once, as the coordinate failure; the argument and
 * return rules then do not run for that method. A field has at most one finding: the arguments are judged before the return
 * type, as the startup does.</p>
 */
@Internal
public final class ResolverClassFindings {

    private ResolverClassFindings() {
    }

    /**
     * @param schema   the schema
     * @param model    the type model
     * @param resolver a concrete resolver class, or an introduction interface that is a resolver bean
     * @return the findings, in declaration order of the methods
     */
    public static List<Finding> check(SchemaModel schema, TypeModel model, JClass resolver) {
        boolean root = resolver.annotations().has(AnnotationNames.ROOT_RESOLVER);
        boolean type = resolver.annotations().has(AnnotationNames.TYPE_RESOLVER);
        if (!root && !type) {
            return List.of();
        }
        Set<String> contracts = contracts(resolver);
        String sourceType = contracts.size() == 1 ? contracts.iterator().next() : null;

        List<JMethod> bindable = resolver.bindableMethods();
        Map<String, JMethod> bySignature = new HashMap<>();
        bindable.forEach(it -> bySignature.put(it.signature(), it));
        Map<String, String> declared = ResolverClassRules.declaredCoordinates(bindable);
        Set<String> rootTypes = schema.rootTypeNames();

        EnumSet<ResolverKind> kinds = EnumSet.noneOf(ResolverKind.class);
        if (root) {
            kinds.add(ResolverKind.ROOT);
        }
        if (type) {
            kinds.add(ResolverKind.TYPE);
        }

        List<Finding> findings = new ArrayList<>();
        Set<String> invalidCoordinates = new HashSet<>();
        if (declared != null) {
            for (JMethod method : bindable) {
                String coordinate = declared.get(method.signature());
                if (coordinate == null) {
                    continue;
                }
                String problem = SchemaCoordinates.check(schema.registry(), rootTypes, coordinate, method.name(), kinds)
                        .orElse(null);
                if (problem != null) {
                    invalidCoordinates.add(method.signature());
                    findings.add(finding(resolver, method, BindingCode.UNKNOWN_SCHEMA_COORDINATE, coordinate,
                            "the @GraphQLSchemaCoordinate on " + method.signature() + " is invalid: " + problem,
                            schema, coordinate,
                            "correct the coordinate to Type.field of a field with the name of the method, or remove it"));
                }
            }
        }

        ClassVerdict verdict = ResolverClassRules.check(schema, model, resolver, sourceType);
        // several methods that can bind one field are an ambiguity of the startup, which judges no argument of them
        Map<String, Integer> claims = new HashMap<>();
        for (MethodVerdict methodVerdict : verdict.methods()) {
            if (!invalidCoordinates.contains(methodVerdict.signature())) {
                methodVerdict.coordinates().forEach(it -> claims.merge(it, 1, Integer::sum));
            }
        }
        Set<String> objectFields = null;
        for (MethodVerdict methodVerdict : verdict.methods()) {
            JMethod method = bySignature.get(methodVerdict.signature());
            if (method == null || invalidCoordinates.contains(method.signature())) {
                continue;
            }
            boolean clean = true;
            for (FieldVerdict field : methodVerdict.fields()) {
                boolean rootField = rootTypes.contains(SchemaCoordinates.typeName(field.coordinate()));
                if (!rootField && sourceType == null || claims.getOrDefault(field.coordinate(), 0) > 1) {
                    // the source type of the fields of the other types is a whole-program fact for this class
                    continue;
                }
                Finding failure = fieldFinding(schema, resolver, method, field, sourceType);
                if (failure != null) {
                    clean = false;
                    findings.add(failure);
                }
            }
            if (!clean) {
                continue;
            }
            if (methodVerdict.coordinates().size() > 1) {
                findings.add(finding(resolver, method, BindingCode.ROOT_METHOD_SEVERAL_OPERATIONS, method.signature(),
                        "the root resolver method " + method.signature() + " binds fields of several operation types: "
                                + String.join(", ", methodVerdict.coordinates()),
                        null, null,
                        "give each operation type its own method with @GraphQLSchemaCoordinate(\"Type.field\")"));
            } else if (declared == null && methodVerdict.coordinates().isEmpty()) {
                // a root resolver binds by the root fields alone; a type resolver is judged by the fields of every object
                // and interface type, and only when it has one contract: with several, the type of a method is not known
                boolean unbound = true;
                if (type) {
                    if (objectFields == null) {
                        objectFields = objectFieldNames(schema);
                    }
                    unbound = contracts.size() == 1 && !objectFields.contains(method.name());
                }
                if (unbound) {
                    findings.add(finding(resolver, method, BindingCode.UNBOUND_RESOLVER_METHOD, method.signature(),
                            "the resolver method " + method.signature() + " binds no schema field",
                            null, null,
                            "annotate it with @GraphQLIgnore, make it non-public, or move it out of the resolver bean"));
                }
            }
        }
        return findings;
    }

    /**
     * @param resolver a resolver class
     * @return the binary names of the classes that the type resolver annotations of the class and its supertypes name
     */
    public static Set<String> contracts(JClass resolver) {
        Set<String> contracts = new TreeSet<>();
        for (JClass supertype : resolver.supertypesAnnotatedWith(AnnotationNames.TYPE_RESOLVER)) {
            contracts.addAll(supertype.annotations().classValues(AnnotationNames.TYPE_RESOLVER, "value"));
        }
        if (resolver.annotations().has(AnnotationNames.TYPE_RESOLVER)) {
            contracts.addAll(resolver.annotations().classValues(AnnotationNames.TYPE_RESOLVER, "value"));
        }
        return contracts;
    }

    @Nullable
    private static Finding fieldFinding(SchemaModel schema, JClass resolver, JMethod method, FieldVerdict field,
                                        @Nullable String sourceType) {
        String coordinate = field.coordinate();
        ArgumentVerdict.Failure failure = field.arguments().failure();
        if (failure instanceof CountMismatch mismatch) {
            String suggested = ResolverClassRules.suggestedMethod(method.name(),
                    SchemaCoordinates.typeName(coordinate), schema.rootTypeNames().contains(
                            SchemaCoordinates.typeName(coordinate)) ? null : sourceType, schemaArguments(schema, coordinate));
            return finding(resolver, method, BindingCode.ARGUMENT_MISMATCH, coordinate,
                    "the method has " + (mismatch.tooMany() ? "more" : "fewer") + " parameters than the field needs: "
                            + mismatch.found() + " found, " + mismatch.required() + " required",
                    schema, coordinate,
                    suggested == null ? "remove the parameters" : "declare it as " + suggested);
        }
        if (failure instanceof ParameterWithoutArgument without) {
            return finding(resolver, method, BindingCode.ARGUMENT_MISMATCH, coordinate,
                    "the parameter " + method.parameters().get(without.parameter()).name() + " binds no argument of "
                            + coordinate + " (arguments: " + String.join(", ", without.available()) + ")",
                    schema, coordinate,
                    "rename the parameter to an argument name, annotate it with @GraphQLArgument(\"<name>\"), or add the "
                            + "argument to the schema");
        }
        if (failure instanceof DuplicateParameter duplicate) {
            return finding(resolver, method, BindingCode.ARGUMENT_MISMATCH, coordinate,
                    "two parameters bind the argument " + duplicate.name() + " of " + coordinate,
                    schema, coordinate, "give each parameter a different argument name");
        }
        if (failure instanceof InvalidSource invalid) {
            return finding(resolver, method, BindingCode.INVALID_SOURCE_PARAMETER, coordinate,
                    "the parameter " + method.parameters().get(invalid.parameter()).name() + " must be the source "
                            + "object of type " + sourceType,
                    schema, coordinate, "declare the source object as the first parameter");
        }
        String returns = field.returns().firstFailure();
        if (returns != null) {
            return finding(resolver, method, BindingCode.UNSUPPORTED_RETURN_TYPE, coordinate,
                    "the return type of " + method.signature() + " cannot be bound to " + coordinate + ": " + returns,
                    schema, coordinate, null);
        }
        return null;
    }

    private static List<ResolverClassRules.SchemaArgument> schemaArguments(SchemaModel schema, String coordinate) {
        FieldDefinition field = fieldDefinition(schema, SchemaCoordinates.typeName(coordinate),
                SchemaCoordinates.fieldName(coordinate));
        if (field == null) {
            return List.of();
        }
        List<ResolverClassRules.SchemaArgument> result = new ArrayList<>();
        for (InputValueDefinition argument : field.getInputValueDefinitions()) {
            result.add(new ResolverClassRules.SchemaArgument(argument.getName(), typeName(argument.getType())));
        }
        return result;
    }

    private static String typeName(graphql.language.Type<?> type) {
        if (type instanceof graphql.language.NonNullType nonNull) {
            return typeName(nonNull.getType());
        }
        if (type instanceof graphql.language.ListType list) {
            return typeName(list.getType());
        }
        return type instanceof graphql.language.TypeName name ? name.getName() : type.toString();
    }

    private static Finding finding(JClass resolver, JMethod method, BindingCode code, String coordinate, String sentence,
                                   @Nullable SchemaModel schema, @Nullable String schemaCoordinate, @Nullable String fix) {
        // the member names the class that declares it; the finding is reported on the class that inherits it
        if (!method.declaringType().name().equals(resolver.name())) {
            sentence += " (inherited by " + resolver.name() + ")";
        }
        SchemaLocation location = null;
        if (schema != null && schemaCoordinate != null) {
            SourceLocation at = schema.locationOf(schemaCoordinate).orElse(null);
            if (at != null && at.getLine() > 0) {
                location = new SchemaLocation(at.getSourceName() == null ? "schema" : at.getSourceName(), at.getLine(),
                        at.getColumn());
            }
        }
        return new Finding(code.code(), coordinate, sentence, method.signature(), null, location, fix);
    }

    @Nullable
    private static FieldDefinition fieldDefinition(SchemaModel schema, String typeName, String fieldName) {
        return schema.registry().getType(typeName)
                .map(it -> it instanceof ObjectTypeDefinition object ? object.getFieldDefinitions()
                        : it instanceof InterfaceTypeDefinition iface ? iface.getFieldDefinitions()
                        : List.<FieldDefinition>of())
                .orElse(List.of()).stream().filter(it -> it.getName().equals(fieldName)).findFirst().orElse(null);
    }

    private static Set<String> objectFieldNames(SchemaModel schema) {
        // the fields of the root types are left out: a type resolver cannot bind them
        Set<String> names = new HashSet<>();
        Set<String> rootTypes = schema.rootTypeNames();
        schema.registry().types().values().forEach(it -> {
            if (rootTypes.contains(it.getName())) {
                return;
            }
            if (it instanceof ObjectTypeDefinition object) {
                object.getFieldDefinitions().forEach(field -> names.add(field.getName()));
            } else if (it instanceof InterfaceTypeDefinition iface) {
                iface.getFieldDefinitions().forEach(field -> names.add(field.getName()));
            }
        });
        return names;
    }
}
