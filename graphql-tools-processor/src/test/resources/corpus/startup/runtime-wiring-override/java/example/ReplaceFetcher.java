package example;

import graphql.schema.idl.RuntimeWiring;
import io.micronaut.graphql.tools.customizer.GraphQLRuntimeWiringCustomizer;
import jakarta.inject.Singleton;

@Singleton
public class ReplaceFetcher implements GraphQLRuntimeWiringCustomizer {

    @Override
    public void customize(RuntimeWiring.Builder builder) {
        builder.type("Query", type -> type.dataFetcher("hello", environment -> "replaced"));
    }

}
