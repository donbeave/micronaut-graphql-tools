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
package io.micronaut.graphql.tools.client.internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Written by the compile-time visitor on each operation method of a client: the checked document and what the runtime needs
 * to send it, so the runtime never parses GraphQL.
 *
 * @author Alexey Zhokhov
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.METHOD)
public @interface GraphQLOperationMetadata {

    /**
     * @return the assembled document: the operation and the fragments it uses
     */
    String document();

    /**
     * @return the name of the operation in the document; empty for an anonymous operation
     */
    String operationName() default "";

    /**
     * @return the type of the operation: {@code query}, {@code mutation} or {@code subscription}
     */
    String operationType() default "query";

}
