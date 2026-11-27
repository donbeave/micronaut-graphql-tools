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

import java.util.List;
import java.util.Objects;

/**
 * A class of name constants: one class for each object, interface and input type that has fields (named {@code <Type>Names}), or
 * the class {@code GraphQLTypeNames} of the names of the other types. The rules decide the class, its package and its constants.
 *
 * @param packageName the package of the class
 * @param simpleName  the simple name of the class
 * @param constants   the constants in schema order
 */
public record NameClass(String packageName, String simpleName, List<NameConstant> constants) {

    /**
     * Creates a class, keeping an immutable copy of the list.
     */
    public NameClass {
        Objects.requireNonNull(packageName, "packageName");
        Objects.requireNonNull(simpleName, "simpleName");
        constants = List.copyOf(constants);
    }
}
