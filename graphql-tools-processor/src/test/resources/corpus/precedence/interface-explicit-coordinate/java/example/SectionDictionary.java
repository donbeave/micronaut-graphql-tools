package example;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer;
import jakarta.inject.Singleton;

@Factory
public class SectionDictionary {

    @Bean
    @Singleton
    GraphQLSchemaMappingDictionaryCustomizer sectionTypes() {
        return dictionary -> dictionary
                .registerType("TextSection", TextSection.class)
                .registerType("MediaSection", MediaSection.class);
    }

}
