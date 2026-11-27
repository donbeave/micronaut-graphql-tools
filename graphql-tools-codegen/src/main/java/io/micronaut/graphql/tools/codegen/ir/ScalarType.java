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
 * A custom scalar that the {@code typeMappings} option maps to a class. A scalar without a mapping is a diagnostic and has no
 * node. No source file is generated for a scalar; the node carries its name for the name constants and its documentation.
 *
 * @param names      the SDL name and the name in the model package
 * @param docs       the description and the deprecation
 * @param className  the qualified name of the class that the scalar is mapped to
 * @param directives the applied directives, without {@code @deprecated}
 * @param origin     where the scalar is defined
 */
public record ScalarType(Names names, Docs docs, String className, List<AppliedDirective> directives, Origin origin) {

    /**
     * Creates a scalar type, keeping an immutable copy of the list.
     */
    public ScalarType {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(docs, "docs");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(origin, "origin");
        directives = List.copyOf(directives);
    }
}
