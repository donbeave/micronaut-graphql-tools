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
import io.micronaut.graphql.tools.mapping.MappingContext;

import java.util.List;
import java.util.Map;

/**
 * Thrown when both a member of the Java type of an object type (a property or a {@code @GraphQLField} method) and a
 * type resolver method can bind one GraphQL field, and {@code micronaut.graphql.tools.precedence} is {@code fail}
 * (the default). Neither the field nor the type resolver method says which one binds it, and
 * {@code micronaut.graphql.tools.strict} does not change that.
 */
@Internal
public final class AmbiguousBindingException extends AbstractMappingException {

    private final Map<Class<?>, List<String>> members;

    public AmbiguousBindingException(MappingContext mappingContext, Map<Class<?>, List<String>> members) {
        super(BindingCode.PRECEDENCE_AMBIGUITY, "Both a member of the Java type and a type resolver method can bind one GraphQL field.", mappingContext);

        this.members = members;
    }

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

        return result.append("\n  Fix it for this field: put a directive listed in ")
                .append("micronaut.graphql.tools.resolver-directives on the field, declare @GraphQLSchemaCoordinate on ")
                .append("the type resolver method (the coordinate of the type, or of the interface that declares the ")
                .append("field), or annotate the Java member with @GraphQLIgnore. Fix it for every field: set ")
                .append("micronaut.graphql.tools.precedence to resolver-first or property-first. ")
                .append("micronaut.graphql.tools.strict does not change this.")
                .toString();
    }

}
