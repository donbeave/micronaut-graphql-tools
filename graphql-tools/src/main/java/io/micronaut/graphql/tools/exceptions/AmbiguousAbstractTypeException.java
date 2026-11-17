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
package io.micronaut.graphql.tools.exceptions;

import java.util.List;

/**
 * Thrown at startup when a class with a bean introspection is an instance of the Java types of several object types of
 * a union or interface, none of them more specific than the others, and is not mapped by its exact class, so that an
 * object of that class could not be resolved to an object type.
 *
 * @since 1.0.0
 */
public final class AmbiguousAbstractTypeException extends RuntimeException {

    private final transient List<String> ambiguities;

    public AmbiguousAbstractTypeException(List<String> ambiguities) {
        super("Objects of these classes cannot be resolved to one object type of a union or interface, because no "
                + "single object type is the most specific one:\n  "
                + String.join("\n  ", ambiguities)
                + "\nMap each class to one of them with @GraphQLType(TheJavaInterface.class), or let it implement "
                + "only one of their Java types.");
        this.ambiguities = List.copyOf(ambiguities);
    }

    public List<String> getAmbiguities() {
        return ambiguities;
    }

}
