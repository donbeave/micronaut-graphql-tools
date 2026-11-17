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
 * Thrown when {@link io.micronaut.graphql.tools.RuntimeWiringCustomizer#claimedFields()} names a coordinate that is not
 * a field of an object type of the schema: the claim is stale or misspelled. All of them are reported in one error.
 */
public final class InvalidClaimedFieldsException extends RuntimeException {

    private final List<String> problems;

    /**
     * @param problems one line per invalid claim, as {@code "<coordinate>: <reason> (<customizer class>)"}
     */
    public InvalidClaimedFieldsException(List<String> problems) {
        super("RuntimeWiringCustomizer beans claim fields that the schema does not define:\n  "
                + String.join("\n  ", problems)
                + "\n  Correct or remove the coordinate in RuntimeWiringCustomizer#claimedFields().");

        this.problems = List.copyOf(problems);
    }

    /**
     * @return one line per invalid claim, as {@code "<coordinate>: <reason> (<customizer class>)"}
     */
    public List<String> getProblems() {
        return problems;
    }

}
