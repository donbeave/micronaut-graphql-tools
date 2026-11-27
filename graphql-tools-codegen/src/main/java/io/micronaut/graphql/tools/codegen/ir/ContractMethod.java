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
package io.micronaut.graphql.tools.codegen.ir;

import io.micronaut.graphql.tools.codegen.Origin;

import java.util.List;
import java.util.Objects;

/**
 * A method of a resolver contract: one root field.
 *
 * @param name       the method name; the field name until a renaming rule exists
 * @param coordinate the field that the method resolves
 * @param returns    the type of the field
 * @param arguments  the arguments of the field in schema order
 * @param wrapper    the return shape
 * @param docs       the description and the deprecation of the field
 * @param directives the applied directives of the field, without {@code @deprecated}
 * @param origin     where the field is defined
 */
public record ContractMethod(String name, Coordinate coordinate, TypeRef returns, List<Argument> arguments, Wrapper wrapper,
                             Docs docs, List<AppliedDirective> directives, Origin origin) {

    /**
     * Creates a method, keeping immutable copies of the lists.
     */
    public ContractMethod {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(returns, "returns");
        Objects.requireNonNull(wrapper, "wrapper");
        Objects.requireNonNull(docs, "docs");
        Objects.requireNonNull(origin, "origin");
        arguments = List.copyOf(arguments);
        directives = List.copyOf(directives);
    }
}
