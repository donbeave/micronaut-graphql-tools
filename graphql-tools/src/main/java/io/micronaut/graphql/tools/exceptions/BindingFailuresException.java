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

import java.util.List;

/**
 * Thrown when more than one field of the schema cannot be bound. Each failure is one of the other exceptions of this
 * package and names its schema coordinate; all of them are reported at once. The first failure is the cause, the
 * others are suppressed exceptions, and {@link #getFailures()} lists them all. A single failure is thrown as is.
 */
public final class BindingFailuresException extends RuntimeException {

    private final List<RuntimeException> failures;

    /**
     * @param failures the failures, at least two, in the order to report them
     */
    public BindingFailuresException(List<? extends RuntimeException> failures) {
        super(buildMessage(failures), failures.get(0));

        this.failures = List.copyOf(failures);
        failures.stream().skip(1).forEach(this::addSuppressed);
    }

    /**
     * @return the failures, in the order they are reported
     */
    public List<RuntimeException> getFailures() {
        return failures;
    }

    private static String buildMessage(List<? extends RuntimeException> failures) {
        StringBuilder builder = new StringBuilder("The schema cannot be bound: ")
                .append(failures.size())
                .append(" errors.");
        int position = 0;
        for (RuntimeException failure : failures) {
            builder.append("\n  ").append(++position).append(") ")
                    .append(failure.getMessage().replace("\n", "\n   "));
        }
        return builder.toString();
    }

}
