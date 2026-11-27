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
package io.micronaut.graphql.tools.binding.internal.client.method;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.internal.model.TypeRef;

/**
 * One parameter of an operation method, as the rules see it.
 *
 * @param name     the name of the parameter
 * @param variable the {@code @GraphQLVariable} value, empty when the parameter binds the variable named like itself
 * @param kind     what binds the parameter
 * @param type     the declared type
 */
@Internal
public record ClientParameter(String name, String variable, Kind kind, TypeRef type) {

    /**
     * What binds a parameter.
     */
    public enum Kind {
        /** A variable of the operation: the default. */
        VARIABLE,
        /** An HTTP header, with {@code @Header}. */
        HEADER,
        /** A cookie, with {@code @CookieValue}. */
        COOKIE,
        /** Any other HTTP binding annotation, which an operation method does not accept. */
        OTHER
    }

    /**
     * @return the name of the variable that the parameter binds
     */
    public String variableName() {
        return variable.isEmpty() ? name : variable;
    }
}
