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

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

/**
 * Thrown when a bean is a {@link GraphQLTypeResolver} only through an inherited annotation, and no type of its class
 * hierarchy shows the annotation to reflection: its contracts, and so the class each of its methods resolves, cannot
 * be read. This happens when class annotations are not available at runtime, for example in a native image without
 * reflection configuration for the contract types.
 */
public final class TypeResolverContractsNotFoundException extends RuntimeException {

    private final Class<?> beanType;

    /**
     * @param beanType the bean class
     */
    public TypeResolverContractsNotFoundException(Class<?> beanType) {
        super("The bean " + beanType.getName() + " is a @" + GraphQLTypeResolver.class.getSimpleName()
                + " only through an inherited annotation, but no type of its class hierarchy declares the annotation "
                + "at runtime, so its contracts cannot be read. Make the annotations of the contract types available "
                + "to reflection (for example the reflection configuration of a native image), or annotate the bean "
                + "class directly.");

        this.beanType = beanType;
    }

    /**
     * @return the bean class
     */
    public Class<?> getBeanType() {
        return beanType;
    }

}
