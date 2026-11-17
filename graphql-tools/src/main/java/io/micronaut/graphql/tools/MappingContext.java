/*
 * Copyright 2021-2022 original authors
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
package io.micronaut.graphql.tools;

import graphql.language.SourceLocation;
import io.micronaut.core.annotation.Nullable;

/**
 * @author Alexey Zhokhov
 */
public interface MappingContext {

    @Nullable
    Class<?> getMappedClass();

    String getMessage(String superMessage);

    /**
     * @return the schema coordinate of the element being mapped: {@code Type.field}, {@code Type.field(argument:)} or
     * {@code InputType.field}; {@code unknown} for an implementation that does not know it
     */
    default String getCoordinate() {
        return "unknown";
    }

    /**
     * @return where the element being mapped is declared in the schema, or {@code null} if the schema was built
     * without source locations or the implementation does not know it
     */
    @Nullable
    default SourceLocation getSourceLocation() {
        return null;
    }

    /**
     * @param coordinate     a schema coordinate
     * @param sourceLocation its source location, or {@code null}
     * @return the coordinate with its source location, for example {@code Query.book (schema.graphqls, line 7, column 3)}
     */
    static String describeCoordinate(String coordinate, @Nullable SourceLocation sourceLocation) {
        if (sourceLocation == null || sourceLocation.getLine() < 0) {
            return coordinate;
        }
        StringBuilder builder = new StringBuilder(coordinate).append(" (");
        if (sourceLocation.getSourceName() != null) {
            builder.append(sourceLocation.getSourceName()).append(", ");
        }
        return builder.append("line ").append(sourceLocation.getLine())
                .append(", column ").append(sourceLocation.getColumn())
                .append(')')
                .toString();
    }

}
