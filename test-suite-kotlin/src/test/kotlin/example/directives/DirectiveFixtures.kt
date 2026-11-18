package example.directives

import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.ResourceResolver
import io.micronaut.graphql.tools.SchemaDirective
import jakarta.inject.Singleton

@Factory
class DirectiveFixtures {

    @Bean
    @Singleton
    @Requires(env = [CONFLICT])
    fun upperForEveryElement(): SchemaDirective =
        SchemaDirective.everyElement(listOf("upper"), object : SchemaDirectiveWiring {})

    @Bean
    @Singleton
    @Requires(env = [UNWIRED])
    @Replaces(TypeDefinitionRegistry::class)
    fun unwiredDirectiveRegistry(resourceResolver: ResourceResolver): TypeDefinitionRegistry =
        registryWith(resourceResolver, "directive @legacy on OBJECT\nextend type Book @legacy")

    @Bean
    @Singleton
    @Requires(env = [SCHEMA_UNLISTED])
    @Replaces(TypeDefinitionRegistry::class)
    fun schemaDirectiveRegistry(resourceResolver: ResourceResolver): TypeDefinitionRegistry =
        registryWith(resourceResolver, "directive @other on SCHEMA\nextend schema @other")

    private fun registryWith(resourceResolver: ResourceResolver, extension: String): TypeDefinitionRegistry {
        val parser = SchemaParser()
        val schema = resourceResolver.getResourceAsStream("classpath:schema.graphqls").orElseThrow().bufferedReader()
        return TypeDefinitionRegistry().merge(parser.parse(schema)).merge(parser.parse(extension))
    }

    companion object {
        const val CONFLICT = "directive-conflict"
        const val UNWIRED = "directive-unwired"
        const val SCHEMA_UNLISTED = "directive-schema-unlisted"
    }
}
