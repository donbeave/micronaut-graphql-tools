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

import java.util.List;

/**
 * A method of a {@link JClass}.
 */
@Internal
public interface JMethod {

    /**
     * @return the method name
     */
    String name();

    /**
     * @return the class that declares the method
     */
    JClass declaringType();

    /**
     * @return the parameters, in declaration order
     */
    List<JParameter> parameters();

    /**
     * @return the generic return type
     */
    TypeRef genericReturnType();

    /**
     * @return whether the method is a Kotlin {@code suspend} function
     */
    boolean suspend();

    /**
     * @return the annotations of the method
     */
    AnnotationView annotations();

    /**
     * @return the method as messages print it, for example {@code a.b.C#m(java.lang.String,int)}
     */
    String signature();
}
