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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Injects a value of the {@code GraphQLContext} of the execution into a resolver method parameter. The parameter is no
 * argument of the field. The key is the annotation value, or the parameter name when the value is empty; a key that the
 * context does not hold injects {@code null}, so the parameter cannot be a primitive. The annotation is inherited:
 * declared on a parameter of an interface or superclass method, it applies to the overriding method.
 */
@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.PARAMETER})
public @interface GraphQLContextValue {

    /**
     * @return the key in the {@code GraphQLContext}, or empty for the name of the parameter
     */
    String value() default "";

}
