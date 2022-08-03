/*
 * Copyright 2021-2022 original authors
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

import io.micronaut.graphql.tools.MappingContext;

/**
 * @author Alexey Zhokhov
 */
public final class InvalidSourceArgumentException extends AbstractMappingException {

    private final Class<?> providedClass;
    private final Class<?> requiredClass;

    public InvalidSourceArgumentException(MappingContext mappingContext, Class<?> providedClass,
                                          Class<?> requiredClass) {
        super(
                String.format(
                        "The source argument must be instance of %s class, provided: %s.",
                        requiredClass.getName(),
                        providedClass.getName()
                ),
                mappingContext
        );

        this.providedClass = providedClass;
        this.requiredClass = requiredClass;
    }

    public Class<?> getProvidedClass() {
        return providedClass;
    }

    public Class<?> getRequiredClass() {
        return requiredClass;
    }

}
