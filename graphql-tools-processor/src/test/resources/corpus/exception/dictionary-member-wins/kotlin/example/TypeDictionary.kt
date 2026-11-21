package example

import io.micronaut.context.annotation.Factory
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer
import jakarta.inject.Singleton

@Factory
class TypeDictionary {

    @Singleton
    fun dictionary(): SchemaMappingDictionaryCustomizer =
        SchemaMappingDictionaryCustomizer { it.registerType("Magazine", MagazineRecord::class.java) }

}
