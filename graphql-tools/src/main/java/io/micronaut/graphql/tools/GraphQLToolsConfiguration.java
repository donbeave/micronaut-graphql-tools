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

    private UnreachableTypes unreachableTypes = UnreachableTypes.FAIL;
    private List<String> allowUnreachable = Collections.emptyList();
    private Precedence precedence = Precedence.STRICT;
    private List<String> resolverDirectives = Collections.emptyList();

    /**
     * @return what happens to object, interface and union types of the schema that no field reachable from a root
     * operation type returns
     */
    @NonNull
    public UnreachableTypes getUnreachableTypes() {
        return unreachableTypes;
    }

    /**
     * Sets what happens to object, interface and union types of the schema that no field reachable from a root
     * operation type returns. Default: {@link UnreachableTypes#FAIL}.
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
     * {@link UnreachableTypes#FAIL}. Every name must be an unreachable object, interface or union type of the schema;
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
     * The handling of object, interface and union types of the schema that no field reachable from a root
     * operation type returns, so that no Java class is bound to them.
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
