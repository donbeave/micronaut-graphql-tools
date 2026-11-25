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
package io.micronaut.graphql.tools.customizer;

import graphql.schema.FieldCoordinates;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.customizer.GraphQLRuntimeWiringCustomizer;
import jakarta.inject.Singleton;

import java.util.Set;

@Requires(property = "spec.name", value = "ClaimedFieldsSpecJava")
@Singleton
public class JavaClaimingCustomizer implements GraphQLRuntimeWiringCustomizer {

    @Override
    public Set<FieldCoordinates> getClaimedFields() {
        return Set.of(FieldCoordinates.coordinates("Query", "version"), FieldCoordinates.coordinates("Book", "isbn"));
    }

    @Override
    public void customize(RuntimeWiring.Builder builder) {
        builder.type("Query", type -> type.dataFetcher("version", new StaticDataFetcher("1.0")));
        builder.type("Book", type -> type.dataFetcher("isbn", new StaticDataFetcher("978-0")));
    }

}
