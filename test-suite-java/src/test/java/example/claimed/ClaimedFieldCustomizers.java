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
package example.claimed;

import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.TypeName;
import graphql.schema.FieldCoordinates;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.RuntimeWiringCustomizer;
import io.micronaut.graphql.tools.TypeDefinitionRegistryCustomizer;
import jakarta.inject.Singleton;

import java.util.Set;

@Factory
@Requires(env = ClaimedFieldCustomizers.ENVIRONMENT)
class ClaimedFieldCustomizers {

    static final String ENVIRONMENT = "claimed-field";

    @Singleton
    TypeDefinitionRegistryCustomizer serverVersionField() {
        return registry -> registry.add(ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition()
                .name("Query")
                .fieldDefinition(FieldDefinition.newFieldDefinition()
                        .name("serverVersion")
                        .type(TypeName.newTypeName("String").build())
                        .build())
                .build());
    }

    @Singleton
    RuntimeWiringCustomizer serverVersionFetcher() {
        return new RuntimeWiringCustomizer() {
            @Override
            public Set<FieldCoordinates> claimedFields() {
                return Set.of(FieldCoordinates.coordinates("Query", "serverVersion"));
            }

            @Override
            public void customize(RuntimeWiring.Builder builder) {
                builder.type("Query", type -> type.dataFetcher("serverVersion", new StaticDataFetcher("1.0")));
            }
        };
    }

}
