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

import io.micronaut.graphql.tools.InputMappingContext;

/**
 * Thrown when a field of a GraphQL input type maps to a Java property that the input {@code ObjectMapper} cannot set:
 * the property is read-only, ignored ({@code @JsonIgnore}) or known to Jackson under another name (a naming
 * strategy, {@code @JsonProperty}). A request that sends the field would fail, or the value would be dropped.
 */
public final class InputFieldNotSettableException extends AbstractMappingException {

    /**
     * @param mappingContext the input field and the mapped property
     */
    public InputFieldNotSettableException(InputMappingContext mappingContext) {
        super(
                String.format(
                        "The input field %s.%s cannot be set: the input ObjectMapper has no settable property `%s` "
                                + "in %s (it is read-only, ignored or renamed for Jackson).",
                        mappingContext.getGraphQlInputObjectType(),
                        mappingContext.getGraphQlInputValue(),
                        mappingContext.getMappedProperty(),
                        mappingContext.getMappedClass().getName()
                ),
                mappingContext
        );
    }

}
