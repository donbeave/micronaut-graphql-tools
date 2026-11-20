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
package io.micronaut.graphql.tools.annotation;

import io.micronaut.core.annotation.Introspected;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a class that represents a GraphQL type: it is introspected, and it is a candidate for the object type named by
 * {@link #name()}.
 *
 * <p>{@link #value()} makes the class an implementation of a Java interface that stands for a GraphQL type.
 * {@link #name()} makes it the class of the object type of that name, so that a union member or an interface
 * implementation needs no {@code SchemaMappingDictionary} entry. The startup and the compile-time checks read the
 * annotation, and a dictionary entry for the same type wins over it. Two classes that match one object type fail
 * with {@code [ambiguous-type-class]}.</p>
 *
 * @author Alexey Zhokhov
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE})
@Introspected
public @interface GraphQLType {

    Class value() default Void.class;

    /**
     * @return the name of the GraphQL object type that this class represents, or an empty string for none. A class
     * with a name is not matched by the naming convention
     */
    String name() default "";

}
