package example;

import graphql.schema.GraphQLScalarType;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import jakarta.inject.Singleton;

import java.time.LocalDate;

@Factory
public class ScalarBeans {

    @Singleton
    GraphQLScalarMapping date() {
        return GraphQLScalarMapping.of(GraphQLScalarType.newScalar().name("Date").coercing(new DateCoercing()).build(), LocalDate.class);
    }

}
