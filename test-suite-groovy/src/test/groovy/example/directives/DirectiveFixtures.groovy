package example.directives

import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.ResourceResolver
import io.micronaut.graphql.tools.SchemaDirective
import jakarta.inject.Singleton

@CompileStatic
@Factory
class DirectiveFixtures {

    static final String CONFLICT = 'directive-conflict'
    static final String UNWIRED = 'directive-unwired'
    static final String SCHEMA_UNLISTED = 'directive-schema-unlisted'

    @Bean
    @Singleton
    @Requires(env = DirectiveFixtures.CONFLICT)
    SchemaDirective upperForEveryElement() {
        SchemaDirective.everyElement(['upper'], new SchemaDirectiveWiring() { })
    }

    @Bean
    @Singleton
    @Requires(env = DirectiveFixtures.UNWIRED)
    @Replaces(TypeDefinitionRegistry)
    TypeDefinitionRegistry unwiredDirectiveRegistry(ResourceResolver resourceResolver) {
        registryWith(resourceResolver, 'directive @legacy on OBJECT\nextend type Book @legacy')
    }

    @Bean
    @Singleton
    @Requires(env = DirectiveFixtures.SCHEMA_UNLISTED)
    @Replaces(TypeDefinitionRegistry)
    TypeDefinitionRegistry schemaDirectiveRegistry(ResourceResolver resourceResolver) {
        registryWith(resourceResolver, 'directive @other on SCHEMA\nextend schema @other')
    }

    private static TypeDefinitionRegistry registryWith(ResourceResolver resourceResolver, String extension) {
        SchemaParser parser = new SchemaParser()
        new TypeDefinitionRegistry()
                .merge(parser.parse(resourceResolver.getResourceAsStream('classpath:schema.graphqls').orElseThrow().newReader()))
                .merge(parser.parse(extension))
    }
}
