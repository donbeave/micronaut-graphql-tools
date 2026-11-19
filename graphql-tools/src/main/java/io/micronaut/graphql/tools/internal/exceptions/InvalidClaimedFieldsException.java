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
 * Thrown when {@link io.micronaut.graphql.tools.RuntimeWiringCustomizer#claimedFields()} names a coordinate that is not
 * a field of an object type of the schema: the claim is stale or misspelled. All of them are reported in one error.
 */
@Internal
public final class InvalidClaimedFieldsException extends SchemaBindingException {

    private final List<String> problems;

    public InvalidClaimedFieldsException(List<String> problems) {
        super(BindingCode.INVALID_CLAIMED_FIELDS, "RuntimeWiringCustomizer beans claim fields that the schema does not define:\n  "
                + String.join("\n  ", problems)
                + "\n  Correct or remove the coordinate in RuntimeWiringCustomizer#claimedFields().");

        this.problems = List.copyOf(problems);
    }

    public List<String> getProblems() {
        return problems;
    }

}
