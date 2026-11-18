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

import io.micronaut.graphql.tools.binding.BindingCode;
import java.util.List;

/**
 * Thrown in strict mode ({@code micronaut.graphql.tools.strict}, on by default) when the binding finds something
 * that works but is probably a mistake: a resolver method that binds no field, a root resolver method that binds
 * fields of several operation types, a Java enum constant without a schema value, a dictionary entry for a type that is
 * not bound, a Java interface read through an implementation. All findings are reported in one error.
 */
public final class StrictModeException extends SchemaBindingException {

    private final List<BindingCode> codes;
    private final List<String> problems;

    /**
     * @param codes    the check code of each problem
     * @param problems the problems, in the order of the codes, without the {@code [code]} prefix
     */
    public StrictModeException(List<BindingCode> codes, List<String> problems) {
        super(BindingCode.STRICT_FINDINGS, buildMessage(codes, problems));

        this.codes = List.copyOf(codes);
        this.problems = List.copyOf(problems);
    }

    /**
     * @return the check code of each problem, in the order of {@link #getProblems()}
     */
    public List<BindingCode> getCodes() {
        return codes;
    }

    public List<String> getProblems() {
        return problems;
    }

    private static String buildMessage(List<BindingCode> codes, List<String> problems) {
        StringBuilder builder = new StringBuilder("Strict mode found problems in the binding of the schema:");
        for (int i = 0; i < problems.size(); i++) {
            builder.append("\n  ").append(i + 1).append(") ").append(codes.get(i).tag(problems.get(i)));
        }
        return builder.append("\n  Fix them, or set micronaut.graphql.tools.strict to false.").toString();
    }

}
