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
package io.micronaut.graphql.tools.internal.exceptions;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.MappingContext;

/**
 * Thrown when the declared Java type of an argument or an input object field cannot be used in that place, e.g. an
 * {@link io.micronaut.graphql.tools.ArgumentValue} on a non-null GraphQL type or inside a list.
 */
@Internal
public final class UnsupportedInputTypeException extends AbstractMappingException {

    private final String reason;

    public UnsupportedInputTypeException(MappingContext mappingContext, String reason) {
        super(BindingCode.UNSUPPORTED_INPUT_TYPE, "The Java type is not supported for this argument or input field: " + reason, mappingContext);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

}
