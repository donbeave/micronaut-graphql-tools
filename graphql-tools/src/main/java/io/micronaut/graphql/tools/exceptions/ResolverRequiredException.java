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
 * Thrown when a GraphQL field carries a directive listed in {@code micronaut.graphql.tools.resolver-directives}, so a
 * type resolver method must bind it, but only a member of the Java type of the object type (a property or a
 * {@code @GraphQLField} method) can.
 */
public final class ResolverRequiredException extends AbstractMappingException {

    private final String directive;
    private final Map<Class<?>, List<String>> members;

    /**
     * @param mappingContext the field
     * @param directive      the directive on the field that requires a type resolver method
     * @param members        the members of the Java type that could bind the field instead, by class
     */
    public ResolverRequiredException(MappingContext mappingContext, String directive,
                                     Map<Class<?>, List<String>> members) {
        super("The GraphQL field carries @" + directive + ", so a type resolver method must bind it, but none does.",
                mappingContext);

        this.directive = directive;
        this.members = members;
    }

    /**
     * @return the directive on the field that requires a type resolver method
     */
    public String getDirective() {
        return directive;
    }

    /**
     * @return the members of the Java type that could bind the field instead, by class
     */
    public Map<Class<?>, List<String>> getMembers() {
        return members;
    }

    @Override
    public String getMessage() {
        StringBuilder result = new StringBuilder(super.getMessage()).append("\n  Not bound:");

        int position = 0;
        for (Map.Entry<Class<?>, List<String>> entry : members.entrySet()) {
            for (String member : entry.getValue().stream().sorted().toList()) {
                result.append("\n  ").append(++position).append(") ").append(entry.getKey().getName()).append(" ")
                        .append(member);
            }
        }

        return result.append("\n  Add a type resolver method for the field, or remove @").append(directive)
                .append(" from it.")
                .toString();
    }

}
