package example

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer
import jakarta.inject.Singleton

@Factory
class SectionDictionary {

    @Bean
    @Singleton
    GraphQLSchemaMappingDictionaryCustomizer sectionTypes() {
        return { dictionary -> dictionary
                .registerType('TextSection', TextSection)
                .registerType('MediaSection', MediaSection) } as GraphQLSchemaMappingDictionaryCustomizer
    }

}
