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
import io.micronaut.graphql.tools.mapping.MappingContext;

/**
 * A field cannot be bound because the application lacks a library that the field's method needs, such as the Kotlin
 * coroutines for a {@code suspend} function ({@link BindingCode#SUSPEND_WITHOUT_COROUTINES}) or a {@code Flow}
 * ({@link BindingCode#FLOW_WITHOUT_REACTIVE_COROUTINES}). It is about one field, so it carries the mapping context and the
 * binding collects it under its own code.
 */
@Internal
public final class FieldSetupException extends AbstractMappingException {

    public FieldSetupException(BindingCode code, String message, MappingContext mappingContext) {
        super(code, message, mappingContext);
    }

}
