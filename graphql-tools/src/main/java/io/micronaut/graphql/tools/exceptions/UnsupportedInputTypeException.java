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
package io.micronaut.graphql.tools.exceptions;

import io.micronaut.graphql.tools.MappingContext;

/**
 * Thrown when the declared Java type of an argument or an input object field cannot be used in that place, e.g. an
 * {@link io.micronaut.graphql.tools.ArgumentValue} without the option that enables it, on a non-null GraphQL type or
 * inside a list.
 */
public final class UnsupportedInputTypeException extends AbstractMappingException {

    private final String reason;

    /**
     * @param mappingContext the coordinate of the argument or input field
     * @param reason         why the type is not supported
     */
    public UnsupportedInputTypeException(MappingContext mappingContext, String reason) {
        super("The Java type is not supported for this argument or input field: " + reason, mappingContext);
        this.reason = reason;
    }

    /**
     * @return why the type is not supported
     */
    public String getReason() {
        return reason;
    }

}
