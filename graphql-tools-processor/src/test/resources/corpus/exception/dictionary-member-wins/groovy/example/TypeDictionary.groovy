package example

import io.micronaut.context.annotation.Factory
import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer
import jakarta.inject.Singleton

@Factory
class TypeDictionary {

    @Singleton
    GraphQLSchemaMappingDictionaryCustomizer dictionary() {
        return { dictionary -> dictionary.registerType('Magazine', MagazineRecord) } as GraphQLSchemaMappingDictionaryCustomizer
    }

}
