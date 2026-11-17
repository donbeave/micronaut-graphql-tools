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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a member that binds no GraphQL field and that strict mode must not report: a resolver method, a getter or
 * property of a type or input class, or an enum constant. A member marked with it is never bound, and is listed as an
 * {@code ~ignored} line of the binding manifest.
 *
 * <p>On an enum constant the annotation is read when the enum has a bean introspection ({@code @Introspected}).</p>
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface GraphQLIgnore {
}
