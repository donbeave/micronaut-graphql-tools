package example

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer
import jakarta.inject.Singleton

@Factory
class NodeDictionary {

    @Bean
    @Singleton
    SchemaMappingDictionaryCustomizer nodeTypes() {
        return { dictionary -> dictionary.registerType('User', User) } as SchemaMappingDictionaryCustomizer
    }

}
