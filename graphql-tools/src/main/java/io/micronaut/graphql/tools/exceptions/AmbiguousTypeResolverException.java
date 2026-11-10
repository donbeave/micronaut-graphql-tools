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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thrown when a bean inherits {@link GraphQLTypeResolver} for several classes (for example it implements two resolver
 * contracts) and some of its methods cannot be assigned to one of them: the first parameter of such a method, the
 * source object, is none of those classes.
 */
public final class AmbiguousTypeResolverException extends RuntimeException {

    private final Class<?> beanType;
    private final List<String> methods;

    /**
     * @param beanType  the bean class
     * @param contracts each type that declares {@link GraphQLTypeResolver}, and its value, in the order to report them
     * @param methods   the methods that cannot be assigned, in the order to report them
     */
    public AmbiguousTypeResolverException(Class<?> beanType, Map<Class<?>, Class<?>> contracts, List<String> methods) {
        super("The bean " + beanType.getName() + " is a @" + GraphQLTypeResolver.class.getSimpleName()
                + " for several classes through "
                + contracts.entrySet().stream()
                .map(it -> it.getKey().getName() + " (" + it.getValue().getName() + ")")
                .collect(Collectors.joining(" and "))
                + ", and the first parameter of these methods is none of those classes, so they cannot be assigned "
                + "to one: " + String.join(", ", methods)
                + ". Make them non-public or give them the source parameter of their contract.");

        this.beanType = beanType;
        this.methods = List.copyOf(methods);
    }

    /**
     * @return the bean class
     */
    public Class<?> getBeanType() {
        return beanType;
    }

    /**
     * @return the methods that cannot be assigned to one contract
     */
    public List<String> getMethods() {
        return methods;
    }

}
