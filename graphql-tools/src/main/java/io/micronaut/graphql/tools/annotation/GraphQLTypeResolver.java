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
package io.micronaut.graphql.tools.annotation;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

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
 * <p>The annotation is {@link Inherited}: it may sit on an interface (for example a generated resolver contract) or a
 * superclass, and every bean implementing or extending it is a type resolver without an annotation of its own.</p>
 *
 * @author Alexey Zhokhov
 */
@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.TYPE})
@Bean
@Executable(processOnStartup = true)
@DefaultScope(Singleton.class)
public @interface GraphQLTypeResolver {

    Class value();

}
