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

/**
 * Adds {@code Query.serverVersion} to the schema, which no Java member binds, and serves it with a data fetcher
 * of a customizer that claims it. The field is added at run time, as the compile-time checks cannot know claims.
 */
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
