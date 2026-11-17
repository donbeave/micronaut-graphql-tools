package io.micronaut.graphql.tools.customizer;

import graphql.schema.FieldCoordinates;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.RuntimeWiringCustomizer;
import jakarta.inject.Singleton;

import java.util.Set;

@Requires(property = "spec.name", value = "ClaimedFieldsSpecJava")
@Singleton
public class JavaClaimingCustomizer implements RuntimeWiringCustomizer {

    @Override
    public Set<FieldCoordinates> claimedFields() {
        return Set.of(FieldCoordinates.coordinates("Query", "version"), FieldCoordinates.coordinates("Book", "isbn"));
    }

    @Override
    public void customize(RuntimeWiring.Builder builder) {
        builder.type("Query", type -> type.dataFetcher("version", new StaticDataFetcher("1.0")));
        builder.type("Book", type -> type.dataFetcher("isbn", new StaticDataFetcher("978-0")));
    }

}
