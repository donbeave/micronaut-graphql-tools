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

import io.micronaut.graphql.tools.MappingContext;

import java.util.List;
import java.util.Map;

/**
 * Thrown when both a member of the Java type of an object type (a property or a {@code @GraphQLField} method) and a
 * type resolver method can bind one GraphQL field, and {@code micronaut.graphql.tools.precedence} is {@code strict}
 * (the default). The field does not carry a directive listed in {@code micronaut.graphql.tools.resolver-directives}.
 */
public final class AmbiguousBindingException extends AbstractMappingException {

    private final Map<Class<?>, List<String>> members;

    /**
     * @param mappingContext the field
     * @param members        the members that can bind the field, by class
     */
    public AmbiguousBindingException(MappingContext mappingContext, Map<Class<?>, List<String>> members) {
        super("Both a member of the Java type and a type resolver method can bind one GraphQL field.", mappingContext);

        this.members = members;
    }

    /**
     * @return the members that can bind the field, by class
     */
    public Map<Class<?>, List<String>> getMembers() {
        return members;
    }

    @Override
    public String getMessage() {
        StringBuilder result = new StringBuilder(super.getMessage()).append("\n  Members:");

        int position = 0;
        for (Map.Entry<Class<?>, List<String>> entry : members.entrySet()) {
            for (String member : entry.getValue().stream().sorted().toList()) {
                result.append("\n  ").append(++position).append(") ").append(entry.getKey().getName()).append(" ")
                        .append(member);
            }
        }

        return result.append("\n  Put a directive listed in micronaut.graphql.tools.resolver-directives on the field to ")
                .append("bind the type resolver method, remove one of the members, or set ")
                .append("micronaut.graphql.tools.precedence to property-first or resolver-first.")
                .toString();
    }

}
