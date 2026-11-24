package example

import io.micronaut.context.annotation.Factory
import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer
import jakarta.inject.Singleton

@Factory
class TypeDictionary {

    @Singleton
    fun dictionary(): GraphQLSchemaMappingDictionaryCustomizer =
        GraphQLSchemaMappingDictionaryCustomizer { it.registerType("Magazine", MagazineRecord::class.java) }

}
