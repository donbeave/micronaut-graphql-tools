package example;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer;
import jakarta.inject.Singleton;

@Factory
public class WrappedDictionary {

    @Bean
    @Singleton
    SchemaMappingDictionaryCustomizer members() {
        return dictionary -> dictionary
                .registerType("Boxed", Boxed.class)
                .registerType("Bagged", Bagged.class);
    }

}
