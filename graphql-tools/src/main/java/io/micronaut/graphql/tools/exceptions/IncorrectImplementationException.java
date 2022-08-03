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
public final class IncorrectImplementationException extends AbstractMappingException {

    private final Class<?> implementationClass;

    public IncorrectImplementationException(MappingContext mappingContext,
                                            Class<?> interfaceClass, Class<?> implementationClass) {
        super(
                String.format(
                        "The annotated implementation class is not implementing the %s interface.",
                        interfaceClass.getName()
                ),
                mappingContext
        );

        this.implementationClass = implementationClass;
    }

    public Class<?> getImplementationClass() {
        return implementationClass;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + "\n  Implementation class: " + implementationClass.getName();
    }

}
