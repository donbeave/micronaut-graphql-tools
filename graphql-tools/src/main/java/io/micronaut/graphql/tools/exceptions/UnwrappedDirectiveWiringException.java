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

import java.util.List;

/**
 * Thrown when the context holds {@link graphql.schema.idl.SchemaDirectiveWiring} beans that no
 * {@link io.micronaut.graphql.tools.SchemaDirective} bean wraps. The library does not apply such a bean on its own:
 * graphql-java would call it for every schema element, merely because it is a bean.
 */
public final class UnwrappedDirectiveWiringException extends RuntimeException {

    private final List<String> beans;

    /**
     * @param beans the unwrapped beans, described by their bean definitions
     */
    public UnwrappedDirectiveWiringException(List<String> beans) {
        super("SchemaDirectiveWiring beans that no SchemaDirective bean wraps: " + String.join(", ", beans)
                + ".\n  The library applies a wiring only through a SchemaDirective: wrap each one in "
                + "SchemaDirective.named(name, wiring) (called where the directive is applied) or "
                + "SchemaDirective.everyElement(names, wiring) (called for every schema element), injecting the bean "
                + "into the SchemaDirective's factory method, or remove the bean.");

        this.beans = List.copyOf(beans);
    }

    /**
     * @return the unwrapped beans, described by their bean definitions
     */
    public List<String> getBeans() {
        return beans;
    }

}
