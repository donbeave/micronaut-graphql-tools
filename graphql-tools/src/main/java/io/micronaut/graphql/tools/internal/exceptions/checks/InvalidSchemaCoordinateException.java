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
package io.micronaut.graphql.tools.internal.exceptions.checks;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;

import java.util.List;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;

/**
 * Thrown when {@link GraphQLSchemaCoordinate} values do not match the schema, the method or the bean they are declared
 * on, or when the method is not bound to the field its coordinate names. All problems are reported in one error.
 */
@Internal
public final class InvalidSchemaCoordinateException extends SchemaBindingException {

    private final List<String> problems;

    public InvalidSchemaCoordinateException(List<String> problems) {
        this(BindingCode.UNKNOWN_SCHEMA_COORDINATE, problems);
    }

    /**
     * @param code     {@code unknown-schema-coordinate}, or {@code coordinate-on-non-resolver} for a coordinate declared
     *                 on a bean that is neither a root nor a type resolver
     * @param problems the problems, one per declaration
     */
    public InvalidSchemaCoordinateException(BindingCode code, List<String> problems) {
        super(code, buildMessage(problems));

        this.problems = List.copyOf(problems);
    }

    public List<String> getProblems() {
        return problems;
    }

    private static String buildMessage(List<String> problems) {
        StringBuilder builder = new StringBuilder("Invalid @")
                .append(GraphQLSchemaCoordinate.class.getSimpleName())
                .append(" declarations:");
        int position = 0;
        for (String problem : problems) {
            builder.append("\n  ").append(++position).append(") ").append(problem);
        }
        return builder.toString();
    }

}
