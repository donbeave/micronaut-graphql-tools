package example.graphql

import example.graphql.type.SecurityError
import example.graphql.type.ValidationError
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.core.io.ResourceResolver
import io.micronaut.graphql.tools.SchemaMappingDictionary
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import jakarta.inject.Singleton

@CompileStatic
@Factory
class GraphQLFactory {

    // tag::registry[]
    @Bean
    @Singleton
    TypeDefinitionRegistry typeDefinitionRegistry(ResourceResolver resourceResolver) {
        InputStream inputStream = resourceResolver.getResourceAsStream('classpath:schema.graphqls').get()
        return new TypeDefinitionRegistry()
                .merge(new SchemaParser().parse(new BufferedReader(new InputStreamReader(inputStream))))
    }
    // end::registry[]

    @Bean
    @Singleton
    SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
        return new SchemaMappingDictionaryCustomizer() {
            @Override
            void customize(SchemaMappingDictionary schemaMappingDictionary) {
                schemaMappingDictionary
                        .registerType('SecurityError', SecurityError)
                        .registerType('ValidationError', ValidationError)
            }
        }
    }
}
