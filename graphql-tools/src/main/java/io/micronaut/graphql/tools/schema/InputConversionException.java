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
package io.micronaut.graphql.tools.schema;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Thrown by a {@link ValueConverter} for a value that does not fit the declared type. {@link #getDetail()} is the
 * problem in GraphQL terms and is what a client sees; {@link #getMessage()} and the cause carry the Java details and are
 * for the log only. The converters of the enclosing input fields and list elements add their place to {@link #getPath()}
 * while the exception unwinds, so nothing is allocated for a value that converts.
 *
 * @since 1.0.0
 */
@Internal
public final class InputConversionException extends RuntimeException {

    private final String detail;
    private final transient List<Object> path = new ArrayList<>();

    /**
     * @param detail   the problem in GraphQL terms, with no Java class names
     * @param internal the message for the log, with the Java details
     * @param cause    the cause, if the failure came from another exception
     */
    public InputConversionException(String detail, String internal, @Nullable Throwable cause) {
        super(internal, cause, false, false);
        this.detail = detail;
    }

    /**
     * @param place an input field name or a list index, within the value that failed
     * @return this exception, with the place in front of the ones it already holds
     */
    public InputConversionException within(Object place) {
        path.add(0, place);
        return this;
    }

    /**
     * @return the problem in GraphQL terms
     */
    public String getDetail() {
        return detail;
    }

    /**
     * @return the input field names (strings) and list indexes (integers) from the argument down to the failed value
     */
    public List<Object> getPath() {
        return path;
    }

}
