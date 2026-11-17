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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Binds a resolver method parameter to the GraphQL field argument with the given name. Without it, the parameter is
 * bound to the argument that has the same name as the parameter. Use it when the parameter name cannot match the
 * argument name, for instance when the argument name is a Java keyword. The annotation is inherited: declared on a
 * parameter of an interface or superclass method, it applies to the overriding method.
 *
 * @author Alexey Zhokhov
 */
@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.PARAMETER})
public @interface GraphQLArgument {

    /**
     * @return the name of the GraphQL field argument the parameter is bound to
     */
    String value();

}
