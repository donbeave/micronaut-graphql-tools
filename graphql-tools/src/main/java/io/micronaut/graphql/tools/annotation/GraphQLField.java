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
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a member of a {@link GraphQLType} class as the one that binds a GraphQL field, and optionally names the field.
 *
 * <p>On a method it makes the method a field resolver, so that a method with arguments (or a
 * {@code DataFetchingEnvironment} parameter) can back a field. With {@link #value()} the member binds the field of
 * that name instead of the field named like the member: it is how a field named like a Java keyword or an
 * {@code Object} method ({@code class}, {@code default}, {@code hashCode}) is bound. The same value on a property (on
 * its field, on its getter, or on the Kotlin or Groovy property) renames the property. A renamed member binds the
 * field of the new name only, so a second member that binds the same field fails the startup with
 * {@code multiple-methods-found}.</p>
 *
 * <p>On a resolver bean the rename is {@link GraphQLSchemaCoordinate}, which names the type as well; this annotation
 * is read on the classes that a resolver maps a type to.</p>
 *
 * @author Alexey Zhokhov
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Executable(processOnStartup = true)
public @interface GraphQLField {

    /**
     * @return the name of the field the member binds; empty (the default) for the name of the member
     */
    String value() default "";

}
