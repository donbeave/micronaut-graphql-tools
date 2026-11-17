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
package io.micronaut.graphql.tools;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;

import java.util.Collections;
import java.util.List;

/**
 * Options of Micronaut GraphQL Tools, under {@value #PREFIX}.
 */
@ConfigurationProperties(GraphQLToolsConfiguration.PREFIX)
public class GraphQLToolsConfiguration {

    /**
     * The prefix of all options.
     */
    public static final String PREFIX = "micronaut.graphql.tools";

    /**
     * The name of an optional {@code com.fasterxml.jackson.databind.ObjectMapper} bean that converts arguments and
     * input objects into their Java types. Without such a bean the library uses a private mapper with Jackson's
     * defaults (among them {@code FAIL_ON_UNKNOWN_PROPERTIES=true}). The library works on a copy of the bean and adds
     * its own modules to the copy, so the bean itself is not changed.
     */
    public static final String INPUT_OBJECT_MAPPER = "graphql-tools";

    private UnreachableTypes unreachableTypes = UnreachableTypes.FAIL;
    private List<String> allowUnreachable = Collections.emptyList();
    private Precedence precedence = Precedence.STRICT;
    private List<String> resolverDirectives = Collections.emptyList();
    private List<String> inertDirectives = Collections.emptyList();
    private boolean strict = true;
    private InputConfiguration input = new InputConfiguration();

    /**
     * @return what happens to object, interface and union types of the schema that no field reachable from a root
     * operation type returns, and in strict mode to enum, input and scalar types that nothing uses
     */
    @NonNull
    public UnreachableTypes getUnreachableTypes() {
        return unreachableTypes;
    }

    /**
     * Sets what happens to object, interface and union types of the schema that no field reachable from a root
     * operation type returns, and in strict mode ({@link #isStrict()}) to enum, input and scalar types that no bound
     * field, argument, input field or directive definition uses. Default: {@link UnreachableTypes#FAIL}.
     *
     * @param unreachableTypes the handling of unreachable types
     */
    public void setUnreachableTypes(@NonNull UnreachableTypes unreachableTypes) {
        ArgumentUtils.requireNonNull("unreachableTypes", unreachableTypes);
        this.unreachableTypes = unreachableTypes;
    }

    /**
     * @return the names of unreachable types that are allowed even when {@link #getUnreachableTypes()} is
     * {@link UnreachableTypes#FAIL}
     */
    @NonNull
    public List<String> getAllowUnreachable() {
        return allowUnreachable;
    }

    /**
     * Sets the names of unreachable types that are allowed even when {@link #getUnreachableTypes()} is
     * {@link UnreachableTypes#FAIL}. Every name must be an unreachable type of the schema: an object, interface or
     * union type, or an enum, input or scalar type (these take effect in strict mode only, see {@link #isStrict()});
     * any other name fails the startup, so the list cannot go stale. Default: empty.
     *
     * @param allowUnreachable the type names
     */
    public void setAllowUnreachable(@NonNull List<String> allowUnreachable) {
        ArgumentUtils.requireNonNull("allowUnreachable", allowUnreachable);
        this.allowUnreachable = List.copyOf(allowUnreachable);
    }

    /**
     * @return how a field is bound when both a member of its Java type (a property or a {@code @GraphQLField} method)
     * and a type resolver method can bind it
     */
    @NonNull
    public Precedence getPrecedence() {
        return precedence;
    }

    /**
     * Sets how a field is bound when both a member of its Java type (a property or a {@code @GraphQLField} method) and
     * a type resolver method can bind it. Default: {@link Precedence#STRICT}.
     *
     * @param precedence the precedence
     */
    public void setPrecedence(@NonNull Precedence precedence) {
        ArgumentUtils.requireNonNull("precedence", precedence);
        this.precedence = precedence;
    }

    /**
     * @return the names of the schema directives that mark a field as bound by a type resolver
     */
    @NonNull
    public List<String> getResolverDirectives() {
        return resolverDirectives;
    }

    /**
     * Sets the names (without {@code @}) of the schema directives that mark a field as bound by a type resolver. A
     * field that carries one of them is bound by its type resolver method, whatever {@link #getPrecedence()} says,
     * and fails the startup when no type resolver method binds it. Every name must be a directive the schema declares.
     * Default: empty.
     *
     * @param resolverDirectives the directive names
     */
    public void setResolverDirectives(@NonNull List<String> resolverDirectives) {
        ArgumentUtils.requireNonNull("resolverDirectives", resolverDirectives);
        this.resolverDirectives = List.copyOf(resolverDirectives);
    }

    /**
     * @return the directives the schema may apply without a wiring, because they have no effect at runtime
     */
    @NonNull
    public List<String> getInertDirectives() {
        return inertDirectives;
    }

    /**
     * Sets the directives the schema may apply without a wiring, because they have no effect at runtime (names
     * without {@code @}). Any other directive the schema applies needs a wiring, except graphql-java's built-in
     * ones; in strict mode ({@link #isStrict()}) an applied directive without either fails the startup, without it
     * that is a warning. Every name must be a directive the schema declares and that has no wiring; any other name
     * fails the startup in either mode, so the list cannot go stale. Default: empty.
     *
     * @param inertDirectives the directive names
     */
    public void setInertDirectives(@NonNull List<String> inertDirectives) {
        ArgumentUtils.requireNonNull("inertDirectives", inertDirectives);
        this.inertDirectives = List.copyOf(inertDirectives);
    }

    /**
     * @return whether binding fails on everything the binding manifest reports as a finding
     */
    public boolean isStrict() {
        return strict;
    }

    /**
     * Sets whether binding fails on everything the binding manifest reports as a finding, unless an option allows it:
     * unreachable types of every kind (also enums, input types and scalars; {@link #getAllowUnreachable()} may then
     * name them too), public resolver methods that bind no field, root resolver methods without a coordinate that bind
     * fields of several operation types, Java enum constants without a schema value,
     * dictionary entries for types that are not bound, Java interfaces read through an implementation's bean
     * introspection instead of their own, input class properties that the input type does not define (unless
     * {@link InputConfiguration#getUnmappedProperties()} is set), primitive arguments or input class properties for
     * nullable built-in scalars, and directives the schema applies without a wiring (unless
     * {@link #getInertDirectives()} lists them; without strict mode they are logged as a warning). Invalid entries of
     * {@link #getAllowUnreachable()} and {@link #getInertDirectives()} fail in either mode. Default: {@code true}.
     *
     * @param strict whether to fail
     */
    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    /**
     * @return the options for arguments and input objects, under {@value #PREFIX}.input
     */
    @NonNull
    public InputConfiguration getInput() {
        return input;
    }

    /**
     * @param input the options for arguments and input objects
     */
    public void setInput(@NonNull InputConfiguration input) {
        ArgumentUtils.requireNonNull("input", input);
        this.input = input;
    }

    /**
     * Options of the binding manifest, under {@value GraphQLToolsConfiguration#PREFIX}{@code .manifest}.
     */
    @ConfigurationProperties("manifest")
    public static class ManifestConfiguration {

        private boolean enabled;
        @Nullable
        private String path;

        /**
         * @return whether the binding manifest is written at startup
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether the binding manifest is written at startup: to {@link #getPath()}, or to the log when no path is
         * set. Default: {@code false}. The {@link BindingManifest} bean is available either way.
         *
         * @param enabled whether to write it
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return the file the binding manifest is written to, or {@code null} to log it
         */
        @Nullable
        public String getPath() {
            return path;
        }

        /**
         * Sets the file the binding manifest is written to at startup; its directories are created. Default: none,
         * the manifest is logged at INFO.
         *
         * @param path the file
         */
        public void setPath(@Nullable String path) {
            this.path = path;
        }

    }

    /**
     * Options for arguments and input objects, under {@value GraphQLToolsConfiguration#PREFIX}.input.
     */
    @ConfigurationProperties("input")
    public static class InputConfiguration {

        private boolean detectOmission;
        @Nullable
        private UnmappedProperties unmappedProperties;

        /**
         * @return whether resolver parameters and input class properties declared as {@link ArgumentValue} tell an
         * omitted value from an explicit {@code null}
         */
        public boolean isDetectOmission() {
            return detectOmission;
        }

        /**
         * Enables {@link ArgumentValue} for resolver parameters and input class properties, so that they tell an
         * omitted value from an explicit {@code null}. Without it a declared {@link ArgumentValue} fails the startup,
         * and an omitted value and an explicit {@code null} both reach the resolver as {@code null}. Default:
         * {@code false}.
         *
         * @param detectOmission whether to enable {@link ArgumentValue}
         */
        public void setDetectOmission(boolean detectOmission) {
            this.detectOmission = detectOmission;
        }

        /**
         * @return what happens to properties of input classes that the GraphQL input type does not define, or
         * {@code null} if strict mode decides ({@link GraphQLToolsConfiguration#isStrict()})
         */
        @Nullable
        public UnmappedProperties getUnmappedProperties() {
            return unmappedProperties;
        }

        /**
         * Sets what happens to properties of input classes (settable through a setter or the constructor) that the
         * GraphQL input type does not define, so no request can ever set them. Either way each of them is an
         * {@code !unbound} line of the binding manifest. Default: not set, which means {@link UnmappedProperties#FAIL}
         * in strict mode ({@link GraphQLToolsConfiguration#isStrict()}) and {@link UnmappedProperties#IGNORE} without
         * it.
         *
         * @param unmappedProperties the handling of unmapped input class properties, or {@code null} to follow strict
         *                           mode
         */
        public void setUnmappedProperties(@Nullable UnmappedProperties unmappedProperties) {
            this.unmappedProperties = unmappedProperties;
        }

    }

    /**
     * How a field is bound when both a member of its Java type (a property or a {@code @GraphQLField} method) and a
     * type resolver method (of the type or of one of its interfaces) can bind it. A field that carries a directive
     * listed in {@link #getResolverDirectives()} is always bound by the type resolver method.
     */
    public enum Precedence {

        /**
         * Fail the startup with {@link io.micronaut.graphql.tools.exceptions.AmbiguousBindingException}, naming the
         * field and both members.
         */
        STRICT,

        /**
         * Bind the member of the Java type; the type resolver method is not called. This was the only behaviour before
         * the option existed.
         */
        PROPERTY_FIRST,

        /**
         * Bind the type resolver method; the member of the Java type is not read.
         */
        RESOLVER_FIRST

    }

    /**
     * The handling of input class properties that the GraphQL input type does not define.
     */
    public enum UnmappedProperties {

        /**
         * Fail the startup with one error that lists them for every input class.
         */
        FAIL,

        /**
         * Accept them (they keep the value the class gives them).
         */
        IGNORE

    }

    /**
     * The handling of object, interface and union types of the schema that no field reachable from a root
     * operation type returns, so that no Java class is bound to them, and in strict mode of enum, input and scalar
     * types that nothing uses.
     */
    public enum UnreachableTypes {

        /**
         * Fail the startup with one error that lists every unreachable type (except those in
         * {@link #getAllowUnreachable()}).
         */
        FAIL,

        /**
         * Keep the types in the schema with graphql-java's default data fetchers, and log them as a warning.
         */
        IGNORE

    }

}
