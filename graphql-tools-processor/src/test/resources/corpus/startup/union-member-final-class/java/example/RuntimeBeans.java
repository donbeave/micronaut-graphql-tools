package example;

import graphql.schema.GraphQLScalarType;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer;
import jakarta.inject.Singleton;

import java.time.LocalDate;

@Factory
public class RuntimeBeans {

    @Singleton
    GraphQLSchemaMappingDictionaryCustomizer dictionary() {
        return dictionary -> dictionary.registerType("Book", Book.class).registerType("Magazine", Magazine.class);
    }

    @Singleton
    GraphQLScalarMapping date() {
        return GraphQLScalarMapping.of(GraphQLScalarType.newScalar().name("Date").coercing(new DateCoercing()).build(), LocalDate.class);
    }

}
