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
package io.micronaut.graphql.tools.binding;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The one registry of the codes of the library: the strict checks and the startup and compile-time failures. A code is
 * the token in {@code [code]} at the start of a diagnostic (at compile time and at startup), the name of a
 * check in the {@code checks} and {@code allow.findings} settings, the kind of a {@code !} line of the binding manifest, and the value
 * of {@code SchemaBindingException#code()}.
 *
 * <p>A code is a lower-case kebab-case word. {@link #compileTime()} says whether the compile-time check reports it; the
 * others are found only at startup, because they need the whole program (the bean set and code-defined wiring).</p>
 */
public enum BindingCode {

    /** Check: a public resolver method binds no schema field. */
    UNBOUND_RESOLVER_METHOD("unbound-resolver-method", true, true,
            "A resolver method binds no schema field."),
    /** Check: a root method binds fields of several operation types. */
    ROOT_METHOD_SEVERAL_OPERATIONS("root-method-several-operations", true, true,
            "A root resolver method binds fields of several operation types."),
    /** Check: an enum constant has no value in the schema enum. */
    UNBOUND_ENUM_CONSTANT("unbound-enum-constant", true, true,
            "A Java enum constant has no value in the schema enum."),
    /** Check: a settable input property has no field in the input type. */
    UNMAPPED_INPUT_PROPERTY("unmapped-input-property", true, true,
            "A settable property of an input class has no field in the GraphQL input type."),
    /** Check: a primitive parameter or input property at a nullable position. */
    NULLABLE_PRIMITIVE("nullable-primitive", true, true,
            "A primitive parameter or input property sits at a nullable schema position."),
    /** Check: an object, interface or union type is not reachable from a root operation type. */
    UNREACHABLE_TYPE("unreachable-type", true, false,
            "The schema defines an object, interface or union type that no root field reaches."),
    /** Check: an enum, input or scalar type is not reachable from a root operation type. */
    UNREACHABLE_VALUE_TYPE("unreachable-value-type", true, false,
            "The schema defines an enum, input or scalar type that no root field reaches."),
    /** Check: a dictionary entry names a type the schema does not bind. */
    UNUSED_DICTIONARY_ENTRY("unused-dictionary-entry", true, false,
            "A SchemaMappingDictionary entry names a type that the schema does not bind."),
    /** Check: an interface without its own introspection is read through an implementation. */
    INTERFACE_READ_THROUGH_IMPLEMENTATION("interface-read-through-implementation", true, false,
            "An interface type is read through one of its implementations."),
    /** Check: a directive is applied and has no wiring. */
    UNWIRED_DIRECTIVE("unwired-directive", true, false,
            "The schema applies a directive that has no wiring."),

    /** Failure: one field can be bound by a property and by a type resolver method, and the precedence is fail. */
    PRECEDENCE_AMBIGUITY("precedence-ambiguity", false, false,
            "A property and a resolver method can both bind one field, and the precedence is fail."),

    /** Failure: a schema coordinate does not match the schema, the method or the bean. */
    UNKNOWN_SCHEMA_COORDINATE("unknown-schema-coordinate", false, true,
            "A @GraphQLSchemaCoordinate value does not match the schema or the method."),
    /** Failure: a coordinate is declared on a class that is not a resolver. */
    COORDINATE_ON_NON_RESOLVER("coordinate-on-non-resolver", false, true,
            "A @GraphQLSchemaCoordinate is declared on a class that is not a resolver."),
    /** Failure: a parameter and a field argument cannot be matched. */
    ARGUMENT_MISMATCH("argument-mismatch", false, true,
            "A resolver parameter and a field argument cannot be matched by name or count."),
    /** Failure: the source parameter does not fit the mapped type. */
    INVALID_SOURCE_PARAMETER("invalid-source-parameter", false, true,
            "The source parameter of a type resolver method does not fit the mapped type."),
    /** Failure: a return type cannot be bound to the field type. */
    UNSUPPORTED_RETURN_TYPE("unsupported-return-type", false, true,
            "The return type of a method cannot be bound to the field type."),
    /** Failure: an argument or input field has a Java type that cannot be used there. */
    UNSUPPORTED_INPUT_TYPE("unsupported-input-type", false, true,
            "The Java type of an argument or input field cannot be used there."),
    /** Failure: an input field maps to a property that cannot be set. */
    INPUT_FIELD_NOT_SETTABLE("input-field-not-settable", false, true,
            "An input field maps to a property that cannot be set."),
    /** Failure: a Kotlin type allows or rejects {@code null} where the schema type does the opposite, and it fails at run time. */
    KOTLIN_NULLABILITY_MISMATCH("kotlin-nullability-mismatch", false, true,
            "A Kotlin return type is nullable where the schema field is non-null, or a Kotlin parameter or input property "
                    + "is non-null where the schema accepts null."),
    /** Failure: a Kotlin type is stricter than the schema type, which is a warning at compile time. */
    KOTLIN_NULLABILITY_LOOSE("kotlin-nullability-loose", false, true,
            "A Kotlin return type is non-null where the schema field is nullable, or a Kotlin parameter or input property "
                    + "is nullable where the schema requires a value; the compiler reports it as a warning."),
    /** Failure: a class that must be introspected is not. */
    CLASS_NOT_INTROSPECTED("class-not-introspected", false, true,
            "A class that backs an input or enum type has no bean introspection."),

    /** Failure: a field has no Java member or method. */
    FIELD_NOT_BOUND("field-not-bound", false, false,
            "No property or method binds a schema field."),
    /** Failure: several methods can bind one field. */
    MULTIPLE_METHODS_FOUND("multiple-methods-found", false, false,
            "Several resolver methods can bind one field."),
    /** Failure: no resolver for a root type. */
    ROOT_RESOLVER_NOT_FOUND("root-resolver-not-found", false, false,
            "The schema has a root operation type and no resolver bean serves it."),
    /** Failure: a field with a resolver directive has no resolver method. */
    RESOLVER_REQUIRED("resolver-required", false, false,
            "A field with a resolver directive has no type resolver method."),
    /** Failure: a mapping failure on a class. */
    INCORRECT_CLASS_MAPPING("incorrect-class-mapping", false, false,
            "A Java class is mapped to a type it cannot serve."),
    /** Failure: a member is mapped outside the declared Java type. */
    INCORRECT_MEMBER_MAPPING("incorrect-member-mapping", false, false,
            "An object type of a union or interface is mapped to a Java type disjoint from the declared one."),
    /** Failure: a mapped implementation does not fit the abstract type. */
    INCORRECT_IMPLEMENTATION("incorrect-implementation", false, false,
            "A mapped implementation does not fit its interface or union."),
    /** Failure: a mapped implementation is missing. */
    IMPLEMENTATION_NOT_FOUND("implementation-not-found", false, false,
            "No class is registered for an object type of an interface or union."),
    /** Failure: several implementations are mapped. */
    MULTIPLE_IMPLEMENTATIONS_FOUND("multiple-implementations-found", false, false,
            "Several classes are registered for one object type."),
    /** Failure: an interface type has no mapping. */
    INTERFACE_TYPE_MAPPING_NOT_PROVIDED("interface-type-mapping-not-provided", false, false,
            "An object type that implements an interface has no registered class."),
    /** Failure: a union type has no mapping. */
    UNION_TYPE_MAPPING_NOT_PROVIDED("union-type-mapping-not-provided", false, false,
            "A member type of a union has no registered class."),
    /** Failure: a type mapping conflicts. */
    MAPPING_CONFLICT("mapping-conflict", false, false,
            "Two mappings of one schema type conflict."),
    /** Failure: a type mapping is ambiguous. */
    AMBIGUOUS_TYPE_MAPPING("ambiguous-type-mapping", false, false,
            "The SchemaMappingDictionary maps object types so that one Java object matches several."),
    /** Failure: the members of an abstract type cannot be told apart. */
    AMBIGUOUS_ABSTRACT_TYPE("ambiguous-abstract-type", false, false,
            "Objects of one class cannot be resolved to one object type of a union or interface."),
    /** Failure: a bean is a type resolver for several classes. */
    AMBIGUOUS_TYPE_RESOLVER("ambiguous-type-resolver", false, false,
            "A bean inherits the type resolver annotation for several classes."),
    /** Failure: a type resolver bean has no contract. */
    TYPE_RESOLVER_CONTRACTS_NOT_FOUND("type-resolver-contracts-not-found", false, false,
            "A bean is a type resolver only through an inherited annotation or the marker TypeResolver<T>, carries the annotation and has a supertype, or implements QueryResolver, MutationResolver or SubscriptionResolver, and the build did not record what the markers mean (the processor is not on the path of the compiler)."),
    /** Failure: an annotation is used wrongly. */
    INCORRECT_ANNOTATION("incorrect-annotation", false, false,
            "A library annotation is used where it has no meaning."),
    /** Failure: enum values are missing from a mapped enum. */
    MISSING_ENUM_VALUES("missing-enum-values", false, false,
            "A schema enum value has no constant in the Java enum."),
    /** Failure: a custom scalar has no mapping. */
    SCALAR_MAPPING_NOT_PROVIDED("scalar-mapping-not-provided", false, false,
            "A custom scalar of the schema has no GraphQLScalarMapping."),
    /** Failure: scalar mappings contradict each other. */
    INVALID_SCALAR_MAPPING("invalid-scalar-mapping", false, false,
            "GraphQLScalarMapping beans contradict each other or the library."),
    /** Failure: a customizer replaced library wiring. */
    RUNTIME_WIRING_OVERRIDE("runtime-wiring-override", false, false,
            "A RuntimeWiringCustomizer replaced what the library wired."),
    /** Failure: a customizer claims fields the schema does not define. */
    INVALID_CLAIMED_FIELDS("invalid-claimed-fields", false, false,
            "A RuntimeWiringCustomizer claims fields that the schema does not define."),
    /** Failure: allow.unreachable names a type that is not unreachable. */
    INVALID_ALLOW_UNREACHABLE("invalid-allow-unreachable", false, false,
            "allow.unreachable names a type that is not an unreachable type."),
    /** Failure: allow.inert-directives lists a directive that cannot be inert. */
    INVALID_INERT_DIRECTIVES("invalid-inert-directives", false, false,
            "allow.inert-directives lists a directive that is built in, undeclared or wired."),
    /**
     * Failure: allow.schema-customizer-directives lists a built-in or undeclared directive, or one never applied to the
     * schema definition or a directive argument.
     */
    INVALID_SCHEMA_CUSTOMIZER_DIRECTIVES("invalid-schema-customizer-directives", false, false,
            "allow.schema-customizer-directives lists a built-in or undeclared directive, or one it never applies to "
                    + "the schema definition or a directive argument."),
    /** Failure: an allow.findings entry is malformed, or matches no finding. */
    INVALID_ALLOW_FINDINGS("invalid-allow-findings", false, false,
            "allow.findings has an entry that is not a manifest line of a check, or that matches no finding."),
    /** Failure: a checks entry names an unknown check or has an unknown severity. */
    INVALID_CHECK_SETTING("invalid-check-setting", false, false,
            "checks names a code that is not a check, or a severity that is not fail, warn or ignore."),
    /** Failure: a configuration key was removed. */
    REMOVED_CONFIGURATION_KEY("removed-configuration-key", false, false,
            "A configuration key was removed; the message names its replacement."),
    /** Failure: a directive is wired twice. */
    DUPLICATE_DIRECTIVE_WIRING("duplicate-directive-wiring", false, false,
            "More than one SchemaDirective binds a wiring to the same directive."),
    /** Failure: a bare SchemaDirectiveWiring bean. */
    UNWRAPPED_DIRECTIVE_WIRING("unwrapped-directive-wiring", false, false,
            "A SchemaDirectiveWiring bean is not wrapped by a SchemaDirective."),
    /** Failure: the schema has no schema definition. */
    SCHEMA_DEFINITION_NOT_PROVIDED("schema-definition-not-provided", false, true,
            "The schema has neither a schema definition nor a Query type."),
    /** Failure: the schema cannot be loaded or merged. */
    SCHEMA_INVALID("schema-invalid", false, true,
            "The schema files cannot be parsed or merged."),
    /** Failure: no schema file is found at the configured locations. */
    SCHEMA_NOT_FOUND("schema-not-found", false, false,
            "No schema file is found at the configured locations, or a location is invalid."),
    /** Failure: the execution executor or an {@code @ExecuteOn} names no executor. */
    UNKNOWN_EXECUTOR("unknown-executor", false, false,
            "micronaut.graphql.tools.execution.executor or an @ExecuteOn names no executor bean, or names one that cannot run work."),
    /** Failure: a resolver needs the execution context and the application replaced the library's invocation. */
    EXECUTION_CONTEXT_UNAVAILABLE("execution-context-unavailable", false, false,
            "A resolver needs the execution context, and the application's GraphQLInvocation replaces the library's."),
    /** Failure: {@code @ExecuteOn} on an accessor that is bound as a property. */
    EXECUTE_ON_PROPERTY("execute-on-property", false, false,
            "@ExecuteOn is on an accessor that is bound as a property, and a property never hops."),
    /** Failure: the application has a DataLoaderRegistry bean, which the library's invocation does not use. */
    DATA_LOADER_REGISTRY_BEAN("data-loader-registry-bean", false, false,
            "A DataLoaderRegistry bean exists, and the library's invocation does not use it."),
    /** Failure: two DataLoader beans have one name. */
    DUPLICATE_DATA_LOADER("duplicate-data-loader", false, false,
            "Two batch loader beans have the same name."),
    /** Failure: a DataLoader parameter matches no batch loader bean, or several. */
    DATA_LOADER_NOT_FOUND("data-loader-not-found", false, false,
            "A DataLoader parameter of a resolver method matches no batch loader bean, or more than one."),
    /** Failure: binding a field threw an exception the library does not know. */
    UNEXPECTED_BINDING_ERROR("unexpected-binding-error", false, false,
            "Binding a field failed with an unexpected exception; the cause is attached."),
    /** Aggregate: more than one binding failure at startup. */
    BINDING_FAILURES("binding-failures", false, false,
            "Several fields or types cannot be bound; each failure carries its own code."),
    /** Aggregate: several strict findings at startup. */
    STRICT_FINDINGS("strict-findings", false, false,
            "The strict checks found problems; each finding carries its own check code."),

    /** Compile-time failure: an unknown processor option. */
    PROCESSOR_OPTION_UNKNOWN("processor-option-unknown", false, true,
            "A micronaut.graphql.tools.processor option is not known."),
    /** Compile-time failure: an invalid processor option value. */
    PROCESSOR_OPTION_INVALID("processor-option-invalid", false, true,
            "A processor option has an invalid value."),
    /** Compile-time failure: a removed processor option. */
    PROCESSOR_OPTION_REMOVED("processor-option-removed", false, true,
            "A processor option was removed; the message names its replacement."),
    /** Compile-time failure: the schema directories are not configured. */
    PROCESSOR_SCHEMA_NOT_CONFIGURED("processor-schema-not-configured", false, true,
            "The processor has no schema directory option."),
    /** Compile-time failure: the configured schema is not found. */
    PROCESSOR_SCHEMA_NOT_FOUND("processor-schema-not-found", false, true,
            "The configured schema directory has no schema file."),
    /** Compile-time failure: an internal error of the processor. */
    PROCESSOR_INTERNAL_ERROR("processor-internal-error", false, true,
            "The processor failed with an internal error.");

    private static final Map<String, BindingCode> BY_CODE;

    static {
        Map<String, BindingCode> byCode = new HashMap<>();
        for (BindingCode value : values()) {
            byCode.put(value.code, value);
        }
        BY_CODE = Collections.unmodifiableMap(byCode);
    }

    private final String code;
    private final boolean check;
    private final boolean compileTime;
    private final String summary;

    BindingCode(String code, boolean check, boolean compileTime, String summary) {
        this.code = code;
        this.check = check;
        this.compileTime = compileTime;
        this.summary = summary;
    }

    /**
     * @return the code token, for example {@code unbound-resolver-method}
     */
    @NonNull
    public String code() {
        return code;
    }

    /**
     * @return {@code true} for a strict check (a key of the {@code checks} setting), {@code false} for a failure
     */
    public boolean check() {
        return check;
    }

    /**
     * @return {@code true} if the compile-time check reports this code, {@code false} if only the startup does
     */
    public boolean compileTime() {
        return compileTime;
    }

    /**
     * @return one sentence about what the code means, for the error reference
     */
    @NonNull
    public String summary() {
        return summary;
    }

    /**
     * @param message a diagnostic text
     * @return the text with {@code [code] } in front
     */
    @NonNull
    public String tag(String message) {
        return "[" + code + "] " + message;
    }

    /**
     * @param code a code token
     * @return the code, or {@code null} if the token is not in the registry
     */
    @Nullable
    public static BindingCode fromCode(String code) {
        return BY_CODE.get(code);
    }

    @Override
    public String toString() {
        return code;
    }
}
