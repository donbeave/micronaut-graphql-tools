package example;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer;
import jakarta.inject.Singleton;

@Factory
public class NodeDictionary {

    @Bean
    @Singleton
    GraphQLSchemaMappingDictionaryCustomizer nodeTypes() {
        return dictionary -> dictionary.registerType("User", User.class);
    }

}
