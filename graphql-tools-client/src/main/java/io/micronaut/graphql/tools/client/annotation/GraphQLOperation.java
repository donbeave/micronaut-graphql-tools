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
 * Marks a method of a {@link GraphQLClient} interface as a GraphQL operation.
 *
 * <p>{@link #value()} is the GraphQL document, or the name of an operation of the client directory when it holds no
 * GraphQL syntax. {@link #name()} selects the operation of a document that holds several. A method without this annotation
 * is left to the Micronaut HTTP client.</p>
 *
 * @author Alexey Zhokhov
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.METHOD)
public @interface GraphQLOperation {

    /**
     * @return the GraphQL document, or the name of an operation file of the client directory; empty for the operation named
     * like the method
     */
    String value() default "";

    /**
     * @return the name of the operation to run; empty for the only operation of the document
     */
    String name() default "";

    /**
     * @return the HTTP method of the request; a mutation or a subscription is always sent with {@link Method#POST}
     */
    Method method() default Method.POST;

    /**
     * The HTTP methods of a GraphQL request.
     */
    enum Method {

        /** The operation is the body of the request. */
        POST,

        /** The operation is in the query string of the request; only a query can use it. */
        GET
    }

}
