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
import java.util.List;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;

/**
 * Thrown when more than one field of the schema cannot be bound. Each failure is one of the other exceptions of this
 * package and names its schema coordinate; all of them are reported at once. The first failure is the cause, the
 * others are suppressed exceptions, and {@link #getFailures()} lists them all. A single failure is thrown as is.
 */
@Internal
public final class BindingFailuresException extends SchemaBindingException {

    private final List<SchemaBindingException> failures;

    public BindingFailuresException(List<? extends SchemaBindingException> failures) {
        super(BindingCode.BINDING_FAILURES, buildMessage(failures), failures.get(0));

        this.failures = List.copyOf(failures);
        failures.stream().skip(1).forEach(this::addSuppressed);
    }

    @Override
    public List<SchemaBindingException> getFailures() {
        return failures;
    }

    private static String buildMessage(List<? extends SchemaBindingException> failures) {
        StringBuilder builder = new StringBuilder("The schema cannot be bound: ")
                .append(failures.size())
                .append(" errors.");
        int position = 0;
        for (SchemaBindingException failure : failures) {
            builder.append("\n  ").append(++position).append(") ")
                    .append(failure.getMessage().replace("\n", "\n   "));
        }
        return builder.toString();
    }

}
