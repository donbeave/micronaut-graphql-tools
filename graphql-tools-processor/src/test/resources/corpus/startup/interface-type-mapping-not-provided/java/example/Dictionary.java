package example;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer;
import jakarta.inject.Singleton;

@Factory
public class Dictionary {

    @Bean
    @Singleton
    GraphQLSchemaMappingDictionaryCustomizer dictionary() {
        return dictionary -> dictionary.registerType("TextSection", TextSection.class);
    }

}
