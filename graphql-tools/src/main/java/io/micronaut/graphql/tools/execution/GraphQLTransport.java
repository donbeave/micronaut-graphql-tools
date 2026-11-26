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
package io.micronaut.graphql.tools.execution;


/**
 * How an operation reached the library's invocation. A minor release may add a constant, so a {@code switch}
 * or {@code when} over it needs a default branch.
 */
public enum GraphQLTransport {

    /** An HTTP request to the GraphQL route. */
    HTTP,

    /** An operation of a WebSocket connection. */
    WEBSOCKET,

    /** A call that has no request. */
    DIRECT

}
