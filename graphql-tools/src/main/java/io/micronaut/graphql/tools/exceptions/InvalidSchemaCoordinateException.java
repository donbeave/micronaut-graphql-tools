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

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;

import java.util.List;

/**
 * Thrown when {@link GraphQLSchemaCoordinate} values do not match the schema, the method or the bean they are declared
 * on, or when the method is not bound to the field its coordinate names. All problems are reported in one error.
 */
public final class InvalidSchemaCoordinateException extends RuntimeException {

    private final List<String> problems;

    /**
     * @param problems one line per invalid coordinate, in the order to report them
     */
    public InvalidSchemaCoordinateException(List<String> problems) {
        super(buildMessage(problems));

        this.problems = List.copyOf(problems);
    }

    /**
     * @return one line per invalid coordinate
     */
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
