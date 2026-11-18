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
package io.micronaut.graphql.tools.binding.internal.model;

import io.micronaut.core.annotation.Internal;

/**
 * A parameter of a {@link JMethod}.
 */
@Internal
public interface JParameter {

    /**
     * @return the parameter name
     */
    String name();

    /**
     * @return whether the name was made up because the class file carries no parameter names ({@code arg0})
     */
    boolean nameIsSynthetic();

    /**
     * @return the generic type
     */
    TypeRef type();

    /**
     * @return the annotations of the parameter
     */
    AnnotationView annotations();
}
