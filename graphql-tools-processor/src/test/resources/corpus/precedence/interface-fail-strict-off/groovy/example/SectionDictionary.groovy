package example

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer
import jakarta.inject.Singleton

@Factory
class SectionDictionary {

    @Bean
    @Singleton
    SchemaMappingDictionaryCustomizer sectionTypes() {
        return { dictionary -> dictionary
                .registerType('TextSection', TextSection)
                .registerType('MediaSection', MediaSection) } as SchemaMappingDictionaryCustomizer
    }

}
