package example;

import graphql.schema.FieldCoordinates;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import io.micronaut.graphql.tools.customizer.GraphQLRuntimeWiringCustomizer;
import jakarta.inject.Singleton;
import java.util.Set;

@Singleton
public class ClaimingCustomizer implements GraphQLRuntimeWiringCustomizer {

    @Override
    public Set<FieldCoordinates> claimedFields() {
        return Set.of(FieldCoordinates.coordinates("Query", "version"));
    }

    @Override
    public void customize(RuntimeWiring.Builder builder) {
        builder.type("Query", type -> type.dataFetcher("version", new StaticDataFetcher("1.0")));
    }

}
