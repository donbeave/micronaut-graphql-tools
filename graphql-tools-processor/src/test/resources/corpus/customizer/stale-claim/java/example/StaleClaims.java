package example;

import graphql.schema.FieldCoordinates;
import graphql.schema.idl.RuntimeWiring;
import io.micronaut.graphql.tools.customizer.RuntimeWiringCustomizer;
import jakarta.inject.Singleton;
import java.util.Set;

@Singleton
public class StaleClaims implements RuntimeWiringCustomizer {

    @Override
    public Set<FieldCoordinates> claimedFields() {
        return Set.of(FieldCoordinates.coordinates("Book", "price"), FieldCoordinates.coordinates("Missing", "field"));
    }

    @Override
    public void customize(RuntimeWiring.Builder builder) {
    }

}
