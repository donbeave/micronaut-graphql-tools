package example;

import graphql.schema.GraphQLScalarType;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.GraphQLScalarMapping;
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer;
import jakarta.inject.Singleton;

import java.time.LocalDate;

@Factory
public class RuntimeBeans {

    @Singleton
    SchemaMappingDictionaryCustomizer dictionary() {
        return dictionary -> dictionary.registerType("Book", Book.class).registerType("Magazine", Magazine.class);
    }

}
