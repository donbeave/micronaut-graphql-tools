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
import io.micronaut.graphql.tools.binding.internal.membership.MemberClassRules;
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames;
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.model.JMethod;
import io.micronaut.graphql.tools.binding.internal.model.TypeModel;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.CountMismatch;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.DuplicateParameter;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.InvalidSource;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.ParameterWithoutArgument;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.UnsatisfiableParameter;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.UntypedDataLoader;
import io.micronaut.graphql.tools.binding.internal.rules.ClassVerdict.FieldVerdict;
import io.micronaut.graphql.tools.binding.internal.rules.ClassVerdict.MethodVerdict;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaCoordinates;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * <p>The findings are those of one stage, as at startup: the declared coordinates are checked first and any problem is the
 * only finding (the startup stops there); then the binding failures of the fields, a field having at most one, the
 * arguments and the input types and enums they reach being judged before the return type; the strict checks
 * ({@code unbound-resolver-method}, {@code root-method-several-operations}, {@code unmapped-input-property},
 * {@code unbound-enum-constant}) only when the class has no binding failure, because the startup runs them after a binding
 * that failed nowhere.</p>
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
        return check(schema, model, resolver, false);
    }

    /**
     * @param schema           the schema
     * @param model            the type model
     * @param resolver         a concrete resolver class, or an introduction interface that is a resolver bean
     * @param accessorPrefixes whether {@code getX} and {@code isX} also bind the field {@code x}
     * @return the findings, in declaration order of the methods
     */
    public static List<Finding> check(SchemaModel schema, TypeModel model, JClass resolver, boolean accessorPrefixes) {
        return checkClass(schema, model, resolver, accessorPrefixes).findings(List.of());
    }

    /**
     * @param schema           the schema
     * @param model            the type model
     * @param resolver         a concrete resolver class, or an introduction interface that is a resolver bean
     * @param accessorPrefixes whether {@code getX} and {@code isX} also bind the field {@code x}
     * @return the findings of the class and the returns that wait for the member classes
     */
    public static ClassFindings checkClass(SchemaModel schema, TypeModel model, JClass resolver, boolean accessorPrefixes) {
        boolean root = resolver.annotations().has(AnnotationNames.ROOT_RESOLVER);
        boolean type = resolver.annotations().has(AnnotationNames.TYPE_RESOLVER);
        if (!root && !type) {
            return only(nonResolver(resolver));
        }
        Set<String> contracts = contracts(resolver);
        String sourceType = contracts.size() == 1 ? contracts.iterator().next() : null;

        List<JMethod> bindable = resolver.bindableMethods();
        Map<String, JMethod> bySignature = new HashMap<>();
        bindable.forEach(it -> bySignature.put(it.signature(), it));
        Map<String, String> declared = ResolverClassRules.declaredCoordinates(bindable);
        Set<String> rootTypes = schema.rootTypeNames();


        List<Finding> findings = new ArrayList<>();
        Set<String> invalidCoordinates = new HashSet<>();
        if (declared != null) {
            for (JMethod method : bindable) {
                String coordinate = declared.get(method.signature());
                if (coordinate == null) {
                    continue;
                }
                String problem = SchemaCoordinates.check(schema.registry(), rootTypes, coordinate,
                        SchemaCoordinates.kindsOf(root, type, resolver.markers(), method.name(), ResolverClassRules.contractArity(method)))
                        .orElse(null);
                if (problem != null) {
                    invalidCoordinates.add(method.signature());
                    findings.add(finding(resolver, method, BindingCode.UNKNOWN_SCHEMA_COORDINATE, coordinate,
                            "the @GraphQLSchemaCoordinate on " + method.signature() + " is invalid: " + problem,
                            schema, coordinate,
                            "correct the coordinate to Type.field of a field with the name of the method, or remove it"));
                } else if (ResolverClassRules.contradictsOperation(schema, resolver, method, coordinate)) {
                    findings.add(finding(resolver, method, BindingCode.UNKNOWN_SCHEMA_COORDINATE, coordinate,
                            "the @GraphQLSchemaCoordinate on " + method.signature() + " names " + coordinate
                                    + ", a field of a root type that the contracts of the method do not bind: they bind "
                                    + operationNames(resolver, method) + " only, so the method is not bound to it",
                            schema, coordinate,
                            "name a field of the root type of the operation of the contract, or move the method to a contract "
                                    + "of the operation of the coordinate"));
                }
            }
        }

        if (!findings.isEmpty()) {
            // the startup reports the invalid and the contradicting coordinates before it binds anything
            return only(findings);
        }

        ClassVerdict verdict = ResolverClassRules.check(schema, model, resolver, sourceType, accessorPrefixes);
        InputBindingFindings inputs = new InputBindingFindings(schema, model);
        List<Finding> failures = new ArrayList<>();
        List<Finding> nullability = new ArrayList<>();
        List<Finding> strict = new ArrayList<>();
        List<MemberClassRules.AbstractReturn> abstractReturns = new ArrayList<>();
        boolean failed = false;
        // several methods that can bind one field are an ambiguity of the startup, which judges no argument of them
        Map<String, Integer> claims = new HashMap<>();
        for (MethodVerdict methodVerdict : verdict.methods()) {
            methodVerdict.coordinates().forEach(it -> claims.merge(it, 1, Integer::sum));
        }
        Set<String> objectFields = null;
        for (MethodVerdict methodVerdict : verdict.methods()) {
            JMethod method = bySignature.get(methodVerdict.signature());
            if (method == null) {
                continue;
            }
            for (FieldVerdict field : methodVerdict.fields()) {
                boolean rootField = rootTypes.contains(SchemaCoordinates.typeName(field.coordinate()));
                if (!rootField && sourceType == null || claims.getOrDefault(field.coordinate(), 0) > 1) {
                    // the source type of the fields of the other types is a whole-program fact for this class
                    continue;
                }
                failed |= !bindField(schema, model, inputs, abstractReturns, failures, nullability, resolver, method, field, sourceType);
            }
            if (methodVerdict.coordinates().stream().map(SchemaCoordinates::typeName).distinct().count() > 1) {
                strict.add(finding(resolver, method, BindingCode.ROOT_METHOD_SEVERAL_OPERATIONS, method.signature(),
                        "the root resolver method " + method.signature() + " binds fields of several operation types: "
                                + String.join(", ", methodVerdict.coordinates()),
                        null, null,
                        "give each operation type its own method with @GraphQLSchemaCoordinate(\"Type.field\")"));
            } else if (declared == null && methodVerdict.coordinates().isEmpty()) {
                // a root resolver binds by the root fields alone; a type resolver is judged by the fields of every object
                // and interface type, and only when it has one contract: with several, the type of a method is not known
                // a method that only the accessor prefixes could bind is not judged: whether they are on is a startup
                // setting, and the compile-time option may differ from it
                boolean unbound;
                List<String> names = FieldNames.ofResolverMethod(method.name(), true);
                if (type) {
                    if (objectFields == null) {
                        objectFields = objectFieldNames(schema);
                    }
                    unbound = contracts.size() == 1 && names.stream().noneMatch(objectFields::contains);
                } else {
                    Set<String> operationTypes = ResolverClassRules.operationTypes(schema, resolver, method);
                    unbound = names.stream().noneMatch(name -> operationTypes.stream()
                            .anyMatch(rootType -> fieldDefinition(schema, rootType, name) != null));
                }
                if (unbound) {
                    strict.add(finding(resolver, method, BindingCode.UNBOUND_RESOLVER_METHOD, method.signature(),
                            "the resolver method " + method.signature() + " binds no schema field",
                            null, null,
                            "annotate it with @GraphQLIgnore, make it private, or move it out of the resolver bean"));
                }
            }
        }
        failures.addAll(inputs.failures());
        // the startup does not read the nullability of Kotlin, so what the rule finds is independent of the stage
        nullability.addAll(inputs.nullability());
        boolean blocked = failed || inputs.halted() || !failures.isEmpty();
        if (!blocked) {
            strict.addAll(inputs.checks());
        }
        return new ClassFindings(List.copyOf(failures), List.copyOf(strict), List.copyOf(nullability), blocked,
                List.copyOf(abstractReturns));
    }

    private static ClassFindings only(List<Finding> findings) {
        return new ClassFindings(findings, List.of(), List.of(), true, List.of());
    }

    private static String operationNames(JClass resolver, JMethod method) {
        return String.join(", ", resolver.markers().operationsOf(method.name(), ResolverClassRules.contractArity(method)));
    }

    private static List<Finding> dedupe(List<Finding> findings) {
        Map<String, Finding> byKey = new LinkedHashMap<>();
        findings.forEach(it -> byKey.putIfAbsent(it.code() + " " + it.coordinate(), it));
        return List.copyOf(byKey.values());
    }

    private static List<Finding> nonResolver(JClass bean) {
        Map<String, String> declared = ResolverClassRules.declaredCoordinates(bean.bindableMethods());
        if (declared == null) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (JMethod method : bean.bindableMethods()) {
            String coordinate = declared.get(method.signature());
            if (coordinate != null) {
                findings.add(finding(bean, method, BindingCode.COORDINATE_ON_NON_RESOLVER, coordinate,
                        "the @GraphQLSchemaCoordinate on " + method.signature() + " is on a bean that is neither a @"
                                + "GraphQLRootResolver nor a @GraphQLTypeResolver",
                        null, null,
                        "annotate the class with @GraphQLRootResolver or @GraphQLTypeResolver, or remove the coordinate"));
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
        // the marker TypeResolver<T> names its source type by its type argument, and the annotation on the marker has none
        contracts.remove("void");
        if (resolver.markers().sourceType() != null) {
            contracts.add(resolver.markers().sourceType());
        }
        return contracts;
    }

    /**
     * Binds one field of a method as the startup does: the arguments (each argument type down to the input classes and
     * enums it reaches), then the return type.
     *
     * @return whether the binding of the field did not fail, so that the startup goes on to the next field
     */
    private static boolean bindField(SchemaModel schema, TypeModel model, InputBindingFindings inputs,
                                     List<MemberClassRules.AbstractReturn> abstractReturns, List<Finding> failures,
                                     List<Finding> nullability, JClass resolver, JMethod method, FieldVerdict field,
                                     @Nullable String sourceType) {
        String coordinate = field.coordinate();
        FieldDefinition definition = fieldDefinition(schema, SchemaCoordinates.typeName(coordinate),
                SchemaCoordinates.fieldName(coordinate));
        ArgumentVerdict.Failure failure = field.arguments().failure();
        if (failure instanceof CountMismatch mismatch) {
            String suggested = ResolverClassRules.suggestedMethod(method.name(),
                    SchemaCoordinates.typeName(coordinate), schema.rootTypeNames().contains(
                            SchemaCoordinates.typeName(coordinate)) ? null : sourceType, schemaArguments(schema, coordinate));
            failures.add(finding(resolver, method, BindingCode.ARGUMENT_MISMATCH, coordinate,
                    "the method has " + (mismatch.tooMany() ? "more" : "fewer") + " parameters than the field needs: "
                            + mismatch.found() + " found, " + mismatch.required() + " required",
                    schema, coordinate,
                    suggested == null ? "remove the parameters" : "declare it as " + suggested));
            return false;
        }
        // the startup binds the type of each argument before it reports a problem with the arguments after it
        if (definition != null) {
            for (ArgumentVerdict.Slot slot : field.arguments().slots()) {
                if (slot.kind() != ArgumentVerdict.SlotKind.ARGUMENT) {
                    continue;
                }
                InputValueDefinition argument = definition.getInputValueDefinitions().stream()
                        .filter(it -> it.getName().equals(slot.argument())).findFirst().orElse(null);
                if (argument != null && !inputs.argument(method.signature(), method.parameters().get(slot.parameter()).type(),
                        argument, coordinate + "(" + slot.argument() + ":)")) {
                    return false;
                }
            }
        }
        if (failure instanceof ParameterWithoutArgument without) {
            failures.add(finding(resolver, method, BindingCode.ARGUMENT_MISMATCH, coordinate,
                    "the parameter " + method.parameters().get(without.parameter()).name() + " binds no argument of "
                            + coordinate + " (arguments: " + String.join(", ", without.available()) + ")",
                    schema, coordinate,
                    without.syntheticName()
                            ? "the class file of " + method.declaringType().name() + " has no parameter names: compile it "
                                    + "with -parameters, or annotate the parameter with @GraphQLArgument(\"<name>\")"
                            : "rename the parameter to an argument name, annotate it with @GraphQLArgument(\"<name>\"), "
                                    + "or add the argument to the schema"));
            return false;
        }
        if (failure instanceof DuplicateParameter duplicate) {
            failures.add(finding(resolver, method, BindingCode.ARGUMENT_MISMATCH, coordinate,
                    "two parameters bind the argument " + duplicate.name() + " of " + coordinate,
                    schema, coordinate, "give each parameter a different argument name"));
            return false;
        }
        if (failure instanceof UntypedDataLoader untyped) {
            failures.add(finding(resolver, method, BindingCode.ARGUMENT_MISMATCH, coordinate,
                    "the parameter " + method.parameters().get(untyped.parameter()).name() + " is a DataLoader without "
                            + "its key and value types",
                    schema, coordinate, "declare it as DataLoader<K, V>, with the key and value types of the loader bean"));
            return false;
        }
        if (failure instanceof UnsatisfiableParameter unsatisfiable) {
            failures.add(finding(resolver, method, BindingCode.ARGUMENT_MISMATCH, coordinate,
                    "the parameter " + method.parameters().get(unsatisfiable.parameter()).name() + " cannot be injected: "
                            + unsatisfiable.reason(),
                    schema, coordinate, null));
            return false;
        }
        if (failure instanceof InvalidSource invalid) {
            failures.add(finding(resolver, method, BindingCode.INVALID_SOURCE_PARAMETER, coordinate,
                    "the parameter " + method.parameters().get(invalid.parameter()).name() + " must be the source "
                            + "object of type " + sourceType,
                    schema, coordinate, "declare the source object as the first parameter"));
            return false;
        }
        String returns = field.returns().firstFailure();
        if (returns != null) {
            failures.add(finding(resolver, method, BindingCode.UNSUPPORTED_RETURN_TYPE, coordinate,
                    "the return type of " + method.signature() + " cannot be bound to " + coordinate + ": " + returns,
                    schema, coordinate, null));
            return false;
        }
        if (definition != null) {
            for (NullabilityRules.Mismatch mismatch : NullabilityRules.returned(method.genericReturnType(),
                    definition.getType(), field.returns())) {
                nullability.add(finding(resolver, method, mismatch.code(), coordinate,
                        mismatch.sentence() + " in " + method.signature(), schema, coordinate, mismatch.fix()));
            }
            InputBindingFindings.Leaf leaf = InputBindingFindings.leaf(method.genericReturnType(), definition.getType(),
                    field.returns());
            if (leaf != null) {
                inputs.returned(method.signature(), leaf.type(), leaf.schemaType(), coordinate,
                        definition.getSourceLocation());
                abstractReturn(schema, model, resolver, method, coordinate, leaf).ifPresent(abstractReturns::add);
            }
        }
        return true;
    }

    // a union or interface that a field returns as a Java interface and that no other field of the schema returns: the field
    // is where the startup reaches it, and binds its members in order
    private static java.util.Optional<MemberClassRules.AbstractReturn> abstractReturn(SchemaModel schema, TypeModel model,
                                                                                      JClass resolver, JMethod method,
                                                                                      String coordinate,
                                                                                      InputBindingFindings.Leaf leaf) {
        if (!(leaf.schemaType() instanceof graphql.language.TypeName name) || leaf.type().array() || leaf.type().primitive()) {
            return java.util.Optional.empty();
        }
        graphql.language.TypeDefinition<?> definition = schema.registry().getType(name.getName()).orElse(null);
        if (!(definition instanceof graphql.language.UnionTypeDefinition || definition instanceof InterfaceTypeDefinition)
                || MemberClassRules.fieldsReturning(schema, name.getName()) != 1
                || model.resolve(leaf.type()).kind() != JClass.Kind.INTERFACE) {
            return java.util.Optional.empty();
        }
        List<String> members;
        if (definition instanceof graphql.language.UnionTypeDefinition union) {
            members = union.getMemberTypes().stream().map(it -> it instanceof graphql.language.TypeName member
                    ? member.getName() : "").toList();
        } else if (definition instanceof InterfaceTypeDefinition) {
            members = schema.registry().getTypes(ObjectTypeDefinition.class).stream()
                    .filter(it -> it.getImplements().stream().anyMatch(implemented ->
                            implemented instanceof graphql.language.TypeName implementedName
                                    && implementedName.getName().equals(name.getName())))
                    .map(ObjectTypeDefinition::getName).sorted().toList();
        } else {
            return java.util.Optional.empty();
        }
        String inheritedBy = method.declaringType().name().equals(resolver.name()) ? null : resolver.name();
        return java.util.Optional.of(new MemberClassRules.AbstractReturn(method.signature(), coordinate,
                location(schema, coordinate), definition instanceof graphql.language.UnionTypeDefinition, name.getName(),
                leaf.type().rawName(), members, inheritedBy));
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
        SchemaLocation location = schema != null && schemaCoordinate != null ? location(schema, schemaCoordinate) : null;
        return new Finding(code.getCode(), coordinate, sentence, method.signature(), null, location, fix);
    }

    @Nullable
    private static SchemaLocation location(SchemaModel schema, String schemaCoordinate) {
        SourceLocation at = schema.locationOf(schemaCoordinate).orElse(null);
        if (at != null && at.getLine() > 0) {
            return new SchemaLocation(at.getSourceName() == null ? "schema" : at.getSourceName(), at.getLine(), at.getColumn());
        }
        return null;
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

    /**
     * What the rules decide about one class, and what they leave to the classes of the whole compilation.
     *
     * @param failures        the binding failures
     * @param strict          the strict checks, which the startup runs only when no class has a binding failure
     * @param nullability     the findings of the Kotlin nullability rule, independent of the stage
     * @param blocked         whether the startup throws while it binds a type of this class, so that it runs no strict check
     * @param abstractReturns the fields that return a union or interface, whose member classes the class does not decide
     */
    public record ClassFindings(List<Finding> failures, List<Finding> strict, List<Finding> nullability, boolean blocked,
                                List<MemberClassRules.AbstractReturn> abstractReturns) {

        /**
         * @param members the binding failures of the member classes of the unions and interfaces the class returns
         * @return the findings of the class: the failures when there is one, else the strict checks, and the nullability findings
         */
        public List<Finding> findings(List<Finding> members) {
            List<Finding> all = new ArrayList<>();
            if (blocked || !members.isEmpty()) {
                // the startup throws the binding failures before it runs a strict check
                all.addAll(failures);
                all.addAll(members);
            } else {
                all.addAll(strict);
            }
            all.addAll(nullability);
            return dedupe(all);
        }
    }
}
