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
 * A property of a {@link JClass}.
 */
@Internal
public interface JProperty {

    /**
     * @return the property name
     */
    String name();

    /**
     * @return the generic type
     */
    TypeRef type();

    /**
     * @return the annotations of the property
     */
    AnnotationView annotations();

    /**
     * @return the accessor as messages print it, for example {@code getTitle()}
     */
    String accessorText();
}
