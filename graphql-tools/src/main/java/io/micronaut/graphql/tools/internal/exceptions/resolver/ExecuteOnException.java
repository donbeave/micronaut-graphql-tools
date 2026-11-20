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
package io.micronaut.graphql.tools.internal.exceptions.resolver;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.internal.exceptions.AbstractMappingException;
import io.micronaut.graphql.tools.mapping.MappingContext;

/**
 * Thrown when the {@code @ExecuteOn} of a field cannot be applied: it names no executor bean
 * ({@link BindingCode#UNKNOWN_EXECUTOR}), or it is on an accessor that binds a property ({@link BindingCode#EXECUTE_ON_PROPERTY}).
 * The message names the cause and the fix.
 */
@Internal
public final class ExecuteOnException extends AbstractMappingException {

    public ExecuteOnException(BindingCode code, String message, MappingContext mappingContext) {
        super(code, message, mappingContext);
    }

}
