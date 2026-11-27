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
package io.micronaut.graphql.tools.binding.internal.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;

import java.util.List;

/**
 * The rules that look at what an operation is sent with, not at the types of its method: the HTTP method and the retry policy
 * of the interface. The sentences live here once, for the visitor and for the code generator.
 */
@Internal
public final class ClientRules {

    private ClientRules() {
    }

    /**
     * @param member        the operation method
     * @param operationType {@code mutation} or {@code subscription}
     * @return the finding for an operation that is sent with GET and is not a query
     */
    public static Finding getOnNonQuery(String member, String operationType) {
        return new Finding(BindingCode.CLIENT_GET_MUTATION.getCode(), member, "the operation is a " + operationType
                + " and is sent with GET, and only a query can be: a GET request must be safe, a server answers a mutation sent with "
                + "GET with 405, and a subscription is not an HTTP request", null, null, null,
                "remove method = GET from @GraphQLOperation, or write the operation as a query");
    }

    /**
     * @param client    the binary name of the interface
     * @param mutations the members of the operations of the interface that are mutations, in declaration order
     * @return the finding for an interface that is retryable as a whole and declares a mutation
     */
    public static Finding retryOnMutation(String client, List<String> mutations) {
        return new Finding(BindingCode.CLIENT_RETRY_MUTATION.getCode(), client, "the interface carries a retry annotation, which "
                + "applies to every method, and declares " + (mutations.size() == 1 ? "a mutation" : mutations.size() + " mutations")
                + ": a retry repeats the mutation when the response is lost, so it can run twice; the mutations are " + mutations,
                null, null, null, "annotate the query methods with @Retryable one by one instead of the interface, or move the "
                + "mutations to an interface without retry");
    }
}
