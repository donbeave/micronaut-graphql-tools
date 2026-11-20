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
package io.micronaut.graphql.tools.binding.internal.model;

import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * A Java class as the rules see it.
 */
@Internal
public interface JClass {

    /**
     * What a class is.
     */
    enum Kind {
        CLASS, ABSTRACT_CLASS, INTERFACE, ENUM, RECORD
    }

    /**
     * @return the binary name, for example {@code a.b.Outer$Inner}
     */
    String name();

    /**
     * @return what the class is
     */
    Kind kind();

    /**
     * @return the annotations of the class, stereotypes included
     */
    AnnotationView annotations();

    /**
     * @return exactly what {@code @Executable(processOnStartup)} exposes on a bean: the public instance methods, inherited
     * ones included, without the {@code Object} methods, the synthetic and bridge methods, the {@code GroovyObject}
     * methods and the Kotlin {@code $default}, {@code componentN} and {@code copy} methods. Empty for a class that is not
     * a bean.
     */
    List<JMethod> bindableMethods();

    /**
     * @return what the marker interfaces fix for the class, {@link ResolverMarkers#NONE} when it implements none
     */
    default ResolverMarkers markers() {
        return ResolverMarkers.NONE;
    }

    /**
     * @return the readable properties, as {@code BeanIntrospection#getBeanProperties()} lists them
     */
    List<JProperty> readableProperties();

    /**
     * @return the constructor parameters and the settable properties, as {@code BeanIntrospection} lists them
     */
    List<JProperty> writableProperties();

    /**
     * @return whether the class has a bean introspection
     */
    boolean introspected();

    /**
     * @return the constants of an enum, in declaration order; empty for any other class
     */
    List<JEnumConstant> enumConstants();

    /**
     * @param binaryName the binary name of a class or interface
     * @return whether the class is that type or has it as a supertype
     */
    boolean isAssignableTo(String binaryName);

    /**
     * @param supertypeBinaryName the binary name of a generic supertype
     * @return the type arguments that this class gives to it, empty when it is not a supertype or is used raw
     */
    List<TypeRef> typeArgumentsOf(String supertypeBinaryName);

    /**
     * @param annotation the binary name of an annotation
     * @return the supertypes of the class that carry the annotation
     */
    List<JClass> supertypesAnnotatedWith(String annotation);
}
