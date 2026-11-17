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
 * Thrown in strict mode ({@code micronaut.graphql.tools.strict}, on by default) when the binding finds something
 * that works but is probably a mistake: a resolver method that binds no field, a root resolver method that binds
 * fields of several operation types, a Java enum constant without a schema value, a dictionary entry for a type that is
 * not bound, a Java interface read through an implementation. All findings are reported in one error.
 */
public final class StrictModeException extends RuntimeException {

    private final List<String> problems;

    /**
     * @param problems one line per finding
     */
    public StrictModeException(List<String> problems) {
        super(buildMessage(problems));

        this.problems = List.copyOf(problems);
    }

    /**
     * @return one line per finding
     */
    public List<String> getProblems() {
        return problems;
    }

    private static String buildMessage(List<String> problems) {
        StringBuilder builder = new StringBuilder("Strict mode found problems in the binding of the schema:");
        int position = 0;
        for (String problem : problems) {
            builder.append("\n  ").append(++position).append(") ").append(problem);
        }
        return builder.append("\n  Fix them, or set micronaut.graphql.tools.strict to false.").toString();
    }

}
