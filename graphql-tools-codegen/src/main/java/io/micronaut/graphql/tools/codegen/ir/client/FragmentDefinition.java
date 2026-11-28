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
package io.micronaut.graphql.tools.codegen.ir.client;

import io.micronaut.graphql.tools.codegen.Origin;
import io.micronaut.graphql.tools.codegen.ir.shared.Names;

import java.util.Objects;

/**
 * A named fragment that at least one operation uses, as the assembled documents carry it. Its generated interface is an
 * {@link io.micronaut.graphql.tools.codegen.ir.AbstractType} of the IR with the same names, which the result models of the
 * operations that spread the fragment implement.
 *
 * @param names         the name of the fragment and the name of its generated interface
 * @param typeCondition the name of the type in the schema that the fragment is on
 * @param selection     the selection set of the fragment, with {@code __typename} injected where the fragment selects an abstract type
 * @param origin        where the document defines the fragment
 */
public record FragmentDefinition(Names names, String typeCondition, SelectionSet selection, Origin origin) {

    /**
     * Creates a fragment.
     */
    public FragmentDefinition {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(typeCondition, "typeCondition");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(origin, "origin");
    }
}
