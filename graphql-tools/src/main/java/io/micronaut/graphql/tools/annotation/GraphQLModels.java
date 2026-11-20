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

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.Introspected;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Makes classes that carry no annotation of their own bind as GraphQL model classes: generated classes, and classes of
 * another module or library. Put it on any type of the module that compiles the resolvers. The compiler generates a bean
 * introspection for every listed class and for every class of a listed package that carries one of the listed
 * annotations, so the startup finds them without scanning the classpath.
 *
 * <p>It is {@code @Introspected(classes, packages, includedAnnotations)} for the GraphQL binding, with the rules of that
 * annotation: a listed package needs {@code includedAnnotations} (the markers that a generator puts on its classes), and
 * the compilation fails without it. The compile-time check also reads the annotation, so a class that it lists, or that
 * carries a marker in a listed package, is not reported as {@code class-not-introspected}. An annotation that lists
 * neither classes nor packages introspects nothing, and the processor warns about it.</p>
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE})
@Introspected
public @interface GraphQLModels {

    /**
     * @return the classes to introspect
     */
    @AliasFor(annotation = Introspected.class, member = "classes")
    Class<?>[] classes() default {};

    /**
     * @return the packages whose classes to introspect, together with {@link #includedAnnotations()}
     */
    @AliasFor(annotation = Introspected.class, member = "packages")
    String[] packages() default {};

    /**
     * @return the annotations that a class of a listed package must carry to be introspected
     */
    @AliasFor(annotation = Introspected.class, member = "includedAnnotations")
    Class<? extends Annotation>[] includedAnnotations() default {};

}
