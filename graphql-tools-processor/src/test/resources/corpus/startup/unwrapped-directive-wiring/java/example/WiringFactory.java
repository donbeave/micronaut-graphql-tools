package example;

import graphql.schema.idl.SchemaDirectiveWiring;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class WiringFactory {

    @Bean
    @Singleton
    SchemaDirectiveWiring upperWiring() {
        return new UpperWiring();
    }

}
