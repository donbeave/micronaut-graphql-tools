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
 * Thrown at startup when the configuration is invalid: a {@code checks} entry that names no check or has no valid
 * severity ({@code invalid-check-setting}), a removed key ({@code removed-configuration-key}) or an
 * {@code allow.findings} entry that is malformed or matches no finding ({@code invalid-allow-findings}).
 */
public final class InvalidConfigurationException extends SchemaBindingException {

    private final List<String> problems;

    /**
     * @param code     one of the configuration codes
     * @param heading  the sentence before the problems
     * @param problems the problems, one per line
     */
    public InvalidConfigurationException(BindingCode code, String heading, List<String> problems) {
        super(code, heading + problems.stream().map(it -> "\n  " + it).reduce("", String::concat));
        this.problems = List.copyOf(problems);
    }

    /**
     * @return the problems, one per setting
     */
    public List<String> getProblems() {
        return problems;
    }

}
