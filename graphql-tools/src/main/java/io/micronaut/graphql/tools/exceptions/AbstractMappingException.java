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
public abstract class AbstractMappingException extends RuntimeException {

    private final transient MappingContext mappingContext;

    protected AbstractMappingException(
            String message,
            MappingContext mappingContext
    ) {
        super(message);
        this.mappingContext = mappingContext;
    }

    /**
     * TODO.
     *
     * @return TODO
     */
    public MappingContext getMappingContext() {
        return mappingContext;
    }

    @Override
    public String getMessage() {
        return mappingContext.getMessage(super.getMessage());
    }

}
