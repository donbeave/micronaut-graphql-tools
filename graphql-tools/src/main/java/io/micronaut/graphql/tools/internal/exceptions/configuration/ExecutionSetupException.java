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
package io.micronaut.graphql.tools.internal.exceptions.configuration;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;

/**
 * The execution of operations cannot be set up at startup: the executor key names no usable executor
 * ({@link BindingCode#UNKNOWN_EXECUTOR}), or a resolver needs the execution context while the application's
 * {@code GraphQLInvocation} replaces the library's ({@link BindingCode#EXECUTION_CONTEXT_UNAVAILABLE}), or a Kotlin {@code suspend} resolver is bound without
 * kotlinx-coroutines-core ({@link BindingCode#SUSPEND_WITHOUT_COROUTINES}). The message
 * names the cause and the way out.
 */
@Internal
public final class ExecutionSetupException extends SchemaBindingException {

    public ExecutionSetupException(BindingCode code, String message) {
        super(code, message);
    }

}
