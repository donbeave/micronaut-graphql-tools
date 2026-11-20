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

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a bean whose public methods bind fields of the GraphQL object type that {@link #value()} is mapped to, by
 * method name. The first parameter of each method is the source object.
 *
 * <p>The annotation carries no bean stereotype: the class is a bean when it has a scope annotation of its own, for example
 * {@code @Singleton}, and one that implements an annotated interface or extends an annotated class is not a bean unless it
 * has one. The marker interface {@code TypeResolver} names the source type by a type argument instead.</p>
 *
 * <p>The annotation is {@link Inherited}: it may sit on an interface (for example a generated resolver contract) or a
 * superclass, and every bean implementing or extending it is a type resolver without an annotation of its own. Methods
 * that carry {@link GraphQLSchemaCoordinate} restrict and validate the binding, see there.</p>
 *
 * @author Alexey Zhokhov
 */
@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.TYPE})
@Requires(condition = ResolverBeanCondition.class)
@Executable(processOnStartup = true)
public @interface GraphQLTypeResolver {

    /**
     * @return the class that the GraphQL object type is mapped to; left out only on the marker interface
     * {@code TypeResolver}, which names it by its type argument
     */
    Class value() default void.class;

}
