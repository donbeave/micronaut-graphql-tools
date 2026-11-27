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
package io.micronaut.graphql.tools.client.call;

import io.micronaut.graphql.tools.client.GraphQLHttpClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The injection points of the specs.
 */
@Singleton
public class Clients {

    @Inject
    @GraphQLClient("stub")
    public GraphQLHttpClient stub;

    @Inject
    @GraphQLClient(id = "slow", endpoint = "/slow")
    public GraphQLHttpClient slow;

    @Inject
    @GraphQLClient(id = "stub", endpoint = "/gated")
    public GraphQLHttpClient gated;

    @Inject
    @GraphQLClient("balanced")
    public GraphQLHttpClient balanced;

    @Inject
    @GraphQLClient("dead")
    public GraphQLHttpClient dead;

    @Inject
    @GraphQLClient(id = "prefixed", endpoint = "/graphql")
    public GraphQLHttpClient prefixed;

    @Inject
    @GraphQLClient(id = "stub", endpoint = "/api/graphql")
    public GraphQLHttpClient nested;
}
