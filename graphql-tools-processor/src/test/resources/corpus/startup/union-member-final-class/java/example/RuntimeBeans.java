package example;

import graphql.schema.GraphQLScalarType;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer;
import jakarta.inject.Singleton;

import java.time.LocalDate;

@Factory
public class RuntimeBeans {

    @Singleton
    SchemaMappingDictionaryCustomizer dictionary() {
        return dictionary -> dictionary.registerType("Book", Book.class).registerType("Magazine", Magazine.class);
    }

    @Singleton
    GraphQLScalarMapping date() {
        return GraphQLScalarMapping.of(GraphQLScalarType.newScalar().name("Date").coercing(new DateCoercing()).build(), LocalDate.class);
    }

}
