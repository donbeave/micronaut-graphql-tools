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
package io.micronaut.graphql.tools.internal.exceptions.mapping;

import io.micronaut.graphql.tools.internal.exceptions.AbstractMappingException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import graphql.schema.GraphQLScalarType;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import io.micronaut.graphql.tools.mapping.MappingContext;

/**
 * Thrown when a field, an argument or an input object field uses a custom scalar of the schema for which no
 * {@link GraphQLScalarMapping} bean exists.
 */
@Internal
public final class ScalarMappingNotProvidedException extends AbstractMappingException {

    private final String scalarName;

    public ScalarMappingNotProvidedException(MappingContext mappingContext, String scalarName,
                                             boolean bareScalarTypeExists) {
        super(BindingCode.SCALAR_MAPPING_NOT_PROVIDED, message(scalarName, bareScalarTypeExists), mappingContext);
        this.scalarName = scalarName;
    }

    private static String message(String scalarName, boolean bareScalarTypeExists) {
        String message = String.format(
                "The GraphQL scalar %s has no mapping. Register a %s bean with its %s and Java classes.",
                scalarName,
                GraphQLScalarMapping.class.getName(),
                GraphQLScalarType.class.getName()
        );
        if (!bareScalarTypeExists) {
            return message;
        }
        return message + String.format(
                " Found a GraphQLScalarType bean '%s'; wrap it with GraphQLScalarMapping.of(type, JavaClass.class).",
                scalarName
        );
    }

    public String getScalarName() {
        return scalarName;
    }

}
