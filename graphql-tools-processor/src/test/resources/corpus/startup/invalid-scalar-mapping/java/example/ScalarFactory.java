package example;

import graphql.Scalars;
import graphql.schema.GraphQLScalarType;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import jakarta.inject.Singleton;

import java.time.LocalDate;

@Factory
public class ScalarFactory {

    private static GraphQLScalarType dateScalar() {
        return GraphQLScalarType.newScalar().name("LocalDate").coercing(Scalars.GraphQLString.getCoercing()).build();
    }

    @Bean
    @Singleton
    GraphQLScalarMapping first() {
        return GraphQLScalarMapping.of(dateScalar(), LocalDate.class);
    }

    @Bean
    @Singleton
    GraphQLScalarMapping second() {
        return GraphQLScalarMapping.of(dateScalar(), LocalDate.class);
    }

}
