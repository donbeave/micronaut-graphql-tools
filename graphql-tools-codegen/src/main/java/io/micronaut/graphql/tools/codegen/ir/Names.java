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

import java.util.Objects;

/**
 * The SDL name of a schema element and the name that the generator gives it, side by side.
 *
 * @param sdlName     the name in the schema; for a resolver contract the coordinate {@code Type.field} of its root field
 * @param packageName the package of the generated type, empty for the default package
 * @param simpleName  the simple name of the generated type
 */
public record Names(String sdlName, String packageName, String simpleName) {

    /**
     * Creates names.
     */
    public Names {
        Objects.requireNonNull(sdlName, "sdlName");
        Objects.requireNonNull(packageName, "packageName");
        Objects.requireNonNull(simpleName, "simpleName");
    }

    /**
     * The qualified name of the generated type.
     *
     * @return the package and the simple name, separated by a dot
     */
    public String qualifiedName() {
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }
}
