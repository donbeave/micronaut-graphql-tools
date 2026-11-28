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
 * A spread of a named fragment in a selection set.
 *
 * @param name       the name of the fragment, which is a {@link FragmentDefinition} of the IR
 * @param directives the directives of the spread in the document
 * @param origin     where the document writes the spread
 */
public record FragmentSpread(String name, List<AppliedDirective> directives, Origin origin) {

    /**
     * Creates a spread, keeping an immutable copy of the list.
     */
    public FragmentSpread {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(origin, "origin");
        directives = List.copyOf(directives);
    }
}
