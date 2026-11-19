package example;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer;
import jakarta.inject.Singleton;

@Factory
public class GhostDictionary {

    @Bean
    @Singleton
    SchemaMappingDictionaryCustomizer ghost() {
        return dictionary -> dictionary.registerType("Ghost", Item.class);
    }

}
