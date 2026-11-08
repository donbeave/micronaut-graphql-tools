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

    /**
     * The name of an optional {@code com.fasterxml.jackson.databind.ObjectMapper} bean that converts arguments and
     * input objects into their Java types. Without such a bean the library uses a private mapper with Jackson's
     * defaults (among them {@code FAIL_ON_UNKNOWN_PROPERTIES=true}). The library works on a copy of the bean and adds
     * its own modules to the copy, so the bean itself is not changed.
     */
    public static final String INPUT_OBJECT_MAPPER = "graphql-tools";

    private UnreachableTypes unreachableTypes = UnreachableTypes.FAIL;
    private List<String> allowUnreachable = Collections.emptyList();
    private InputConfiguration input = new InputConfiguration();

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
     * Options for arguments and input objects, under {@value GraphQLToolsConfiguration#PREFIX}.input.
     */
    @ConfigurationProperties("input")
    public static class InputConfiguration {

        private boolean detectOmission;

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
