package example.tck

import example.graphql.GraphQLFactory
import example.graphql.type.SecurityError
import example.graphql.type.ValidationError
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.ResourceResolver
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.tck.Scenarios
import io.micronaut.graphql.tools.tck.Tck
import jakarta.inject.Singleton
import org.dataloader.DataLoaderRegistry

/** While a TCK scenario runs, the application serves the example schema and the extensions of that scenario's schema. */
@Factory
@Requires(property = Tck.SCENARIO_PROPERTY)
class TckGraphQLFactory {

    @Bean
    @Singleton
    @Replaces(value = TypeDefinitionRegistry::class, factory = GraphQLFactory::class)
    fun typeDefinitionRegistry(
        @Property(name = Tck.SCENARIO_PROPERTY) scenario: String,
        resourceResolver: ResourceResolver,
    ): TypeDefinitionRegistry {
        val parser = SchemaParser()
        val example = resourceResolver.getResourceAsStream("classpath:schema.graphqls").get().use { it.readBytes().decodeToString() }
        return TypeDefinitionRegistry()
            .merge(parser.parse(example))
            .merge(parser.parse(Scenarios.schema(scenario)))
    }

    // The example registers the classes of its union by name; a scenario with classes of that kind adds its own mappings.
    @Bean
    @Singleton
    @Replaces(value = SchemaMappingDictionaryCustomizer::class, factory = GraphQLFactory::class)
    fun schemaMappingDictionaryCustomizer(scenarioMappings: List<TckTypeMappings>) = SchemaMappingDictionaryCustomizer { dictionary ->
        dictionary
            .registerType("SecurityError", SecurityError::class.java)
            .registerType("ValidationError", ValidationError::class.java)
        scenarioMappings.forEach { it.register(dictionary) }
    }

    // The example's registry is request scoped, and a request-scoped proxy has no request on the thread that completes a
    // CompletionStage. Every scenario but the one that tests a registry for each request (tck.registry=per-request) serves a
    // plain registry.
    @Bean
    @Singleton
    @Primary
    @Requires(missingProperty = "tck.registry")
    fun dataLoaderRegistry(): DataLoaderRegistry = DataLoaderRegistry()
}
