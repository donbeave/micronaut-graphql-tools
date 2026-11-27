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
package io.micronaut.graphql.tools.client.declarative;

import io.micronaut.graphql.tools.client.GraphQLClientAttributes;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A filter of the service {@code decl}: it runs for the operation methods of the declarative clients and shows the attributes
 * that the client sets.
 */
@ClientFilter(serviceId = "decl")
public class DeclFilter {

    public static final AtomicInteger CALLS = new AtomicInteger();

    @RequestFilter
    public void filter(MutableHttpRequest<?> request) {
        CALLS.incrementAndGet();
        request.getAttribute(GraphQLClientAttributes.OPERATION_NAME, String.class).ifPresent(name -> request.header("X-Operation-Name", name));
        request.getAttribute(GraphQLClientAttributes.OPERATION_TYPE).ifPresent(type -> request.header("X-Operation-Type", type.toString()));
    }
}
