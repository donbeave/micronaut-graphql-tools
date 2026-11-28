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
import io.micronaut.graphql.tools.codegen.ir.shared.AppliedDirective;

import java.util.List;
import java.util.Objects;

/**
 * An inline fragment of a selection set.
 *
 * @param typeCondition the name of the type in the schema, or {@code null} for a fragment without a type condition
 * @param selection     the selected fields
 * @param directives    the directives of the fragment in the document
 * @param origin        where the document writes the fragment
 */
public record InlineFragment(String typeCondition, SelectionSet selection, List<AppliedDirective> directives, Origin origin) {

    /**
     * Creates an inline fragment, keeping an immutable copy of the list.
     */
    public InlineFragment {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(origin, "origin");
        directives = List.copyOf(directives);
    }
}
