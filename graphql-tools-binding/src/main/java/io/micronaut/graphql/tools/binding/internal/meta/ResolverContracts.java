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
package io.micronaut.graphql.tools.binding.internal.meta;

import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * What a concrete resolver class inherits: the contracts of a type resolver, the root operations that its marker
 * interfaces fix and the methods of its contract interfaces, each with the operations and the source type of its contract.
 * The visitor writes it into the annotation metadata of the class and the runtime reads it from the bean definition, so that no reflection is needed to find a class annotation
 * or a generic type argument of a supertype. The visitor writes it, also when the checks are off.
 */
@Internal
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ResolverContracts {

    /**
     * @return the model classes of the contracts, in the order of {@link #declaredBy()}
     */
    Class<?>[] value() default {};

    /**
     * @return the binary names of the contract types that declare each model class
     */
    String[] declaredBy() default {};

    /**
     * @return the root operations ({@code query}, {@code mutation}, {@code subscription}) that the marker interfaces of
     * the class name; empty when no marker names one
     */
    String[] operations() default {};

    /**
     * @return the methods of the contract interfaces of the class, one entry per method of each contract, as
     * {@code operations|model|name/arity} (see {@code ResolverMarkers.ContractMethod}); a method of the class that has
     * the name and the number of parameters of an entry implements it. Empty when every public method is a candidate
     */
    String[] contractMethods() default {};
}
