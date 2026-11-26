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
package io.micronaut.graphql.tools.client.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Binds a parameter of a {@link GraphQLOperation} method to a variable of the operation.
 *
 * <p>A parameter without this annotation binds the variable named like the parameter.</p>
 *
 * @author Alexey Zhokhov
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.PARAMETER)
public @interface GraphQLVariable {

    /**
     * @return the name of the variable, without the dollar sign; empty for the name of the parameter
     */
    String value() default "";

}
