package example.graphql

import example.graphql.type.SecurityError
import example.graphql.type.ValidationError
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.core.io.ResourceResolver
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import jakarta.inject.Singleton

@Factory
class GraphQLFactory {

    // tag::registry[]
    @Bean
    @Singleton
    fun typeDefinitionRegistry(resourceResolver: ResourceResolver): TypeDefinitionRegistry {
        val inputStream = resourceResolver.getResourceAsStream("classpath:schema.graphqls").get()
        return TypeDefinitionRegistry().merge(SchemaParser().parse(inputStream.bufferedReader()))
    }
    // end::registry[]

    @Bean
    @Singleton
    fun schemaMappingDictionaryCustomizer() = SchemaMappingDictionaryCustomizer { schemaMappingDictionary ->
        schemaMappingDictionary
            .registerType("SecurityError", SecurityError::class.java)
            .registerType("ValidationError", ValidationError::class.java)
    }
}
