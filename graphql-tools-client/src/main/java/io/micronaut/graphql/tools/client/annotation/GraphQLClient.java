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

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.annotation.Client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a GraphQL client. It is a stereotype of {@link Client}, so the service id, the path and the configuration
 * resolve like those of any Micronaut HTTP client, and {@code micronaut.http.services.<id>.*} applies.
 *
 * <p>On an interface it declares a client whose methods, annotated with {@link GraphQLOperation}, are the operations.
 * On a field or a parameter it marks a low-level injection point: the client is built for the service id of the
 * annotation.</p>
 *
 * @author Alexey Zhokhov
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Client
public @interface GraphQLClient {

    /**
     * @return the service id or the URL of the server; an alias of {@link #id()} and of {@link Client#id()}
     */
    @AliasFor(annotation = Client.class, member = "id")
    @AliasFor(member = "id")
    String value() default "";

    /**
     * @return the service id or the URL of the server; an alias of {@link #value()} and of {@link Client#id()}
     */
    @AliasFor(annotation = Client.class, member = "id")
    @AliasFor(member = "value")
    String id() default "";

    /**
     * @return the path of the GraphQL endpoint on the server; an alias of {@link Client#path()}
     */
    @AliasFor(annotation = Client.class, member = "path")
    String endpoint() default "";

    /**
     * @return the path of the GraphQL WebSocket endpoint used by subscriptions; empty for the value of {@link #endpoint()}
     */
    String webSocketEndpoint() default "";

    /**
     * @return the name of the client schema that the operations of this client are checked against at compile time; empty
     * for the schema of the client without a name
     */
    String schema() default "";

    /**
     * @return the HTTP client configuration class; an alias of {@link Client#configuration()}
     */
    @AliasFor(annotation = Client.class, member = "configuration")
    Class<? extends HttpClientConfiguration> configuration() default HttpClientConfiguration.class;

}
