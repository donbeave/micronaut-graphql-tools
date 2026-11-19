package example;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer;
import jakarta.inject.Singleton;

@Factory
public class SectionDictionary {

    @Bean
    @Singleton
    SchemaMappingDictionaryCustomizer sectionTypes() {
        return dictionary -> dictionary
                .registerType("TextSection", TextSection.class)
                .registerType("MediaSection", MediaSection.class);
    }

}
