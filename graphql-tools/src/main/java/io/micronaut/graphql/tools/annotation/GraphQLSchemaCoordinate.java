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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The schema coordinate ({@code Type.field}) of the GraphQL field that a method of a {@link GraphQLRootResolver} or
 * {@link GraphQLTypeResolver} bean binds, for example {@code @GraphQLSchemaCoordinate("Book.summary")}.
 *
 * <p>Meant for resolver contracts, such as generated interfaces: the annotation is {@link Inherited}, so it stays on
 * the method that implements the contract method, and it is meta-annotated
 * {@code @Executable(processOnStartup = true)}, so the contract methods are executable on the implementing bean.</p>
 *
 * <p>The coordinate is checked at startup, never trusted. The startup fails when:</p>
 * <ul>
 *     <li>the bean is neither a {@link GraphQLRootResolver} nor a {@link GraphQLTypeResolver};</li>
 *     <li>the value is not of the form {@code Type.field}, the schema has no such object type, or the type has no
 *     such field;</li>
 *     <li>the field name differs from the method name;</li>
 *     <li>the type is not a root operation type on a {@link GraphQLRootResolver}, or is one on a
 *     {@link GraphQLTypeResolver};</li>
 *     <li>the method does not end up bound to exactly that field, for example because the type is mapped to a class
 *     that the {@link GraphQLTypeResolver} does not resolve, a property of the mapped class binds the field, or the
 *     same method name also binds a field of another root operation type.</li>
 * </ul>
 *
 * <p>On a bean where at least one method carries the annotation, only the annotated methods bind; other public
 * methods, such as helpers, are not candidates. Fields are still matched by method name.</p>
 */
@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.METHOD})
@Executable(processOnStartup = true)
public @interface GraphQLSchemaCoordinate {

    /**
     * @return the schema coordinate of the bound field, {@code Type.field}
     */
    String value();

}
