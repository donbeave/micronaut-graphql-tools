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
 * An object type that is generated as a model (a record or a bean). The root operation types are not models: they are
 * {@link ResolverContract}s.
 *
 * @param names       the SDL name and the generated name
 * @param docs        the description and the deprecation
 * @param fields      the fields in schema order, the fields of an extension after those of the base definition
 * @param memberOf    the unions that list this type as a member, in schema order; only the unions that are generated
 * @param implemented the interfaces that the type implements, in schema order; only the interfaces that are generated
 * @param directives  the applied directives, without {@code @deprecated}
 * @param origin      where the type is defined
 */
public record ModelType(Names names, Docs docs, List<Field> fields, List<Names> memberOf, List<Names> implemented,
                        List<AppliedDirective> directives, Origin origin) {

    /**
     * Creates a model type, keeping immutable copies of the lists.
     */
    public ModelType {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(docs, "docs");
        Objects.requireNonNull(origin, "origin");
        fields = List.copyOf(fields);
        memberOf = List.copyOf(memberOf);
        implemented = List.copyOf(implemented);
        directives = List.copyOf(directives);
    }
}
