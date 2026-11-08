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
    private List<String> inertDirectives = Collections.emptyList();

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
     * @return the directives the schema may apply without a wiring, because they have no effect at runtime
     */
    @NonNull
    public List<String> getInertDirectives() {
        return inertDirectives;
    }

    /**
     * Sets the directives the schema may apply without a wiring, because they have no effect at runtime (names
     * without {@code @}). Any other directive the schema applies needs a wiring, except graphql-java's built-in
     * ones. Every name must be a directive the schema declares and that has no wiring; any other name fails the
     * startup, so the list cannot go stale. Default: empty.
     *
     * @param inertDirectives the directive names
     */
    public void setInertDirectives(@NonNull List<String> inertDirectives) {
        ArgumentUtils.requireNonNull("inertDirectives", inertDirectives);
        this.inertDirectives = List.copyOf(inertDirectives);
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
