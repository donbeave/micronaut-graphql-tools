package example.graphql.directive

import graphql.schema.DataFetcher
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.graphql.tools.GraphQLSchemaCustomizer
import io.micronaut.graphql.tools.SchemaDirective
import jakarta.inject.Singleton

@CompileStatic
@Factory
class DirectiveWirings {

    @Bean
    @Singleton
    SchemaDirective upper() {
        SchemaDirective.named('upper', new UpperWiring())
    }

    @Bean
    @Singleton
    SchemaDirective tagged() {
        SchemaDirective.everyElement(['tagged'], new TaggedWiring())
    }

    @Bean
    @Singleton
    GraphQLSchemaCustomizer audit() {
        new AuditCustomizer()
    }

    static class AuditCustomizer implements GraphQLSchemaCustomizer {
        @Override
        graphql.schema.GraphQLSchema customize(graphql.schema.GraphQLSchema schema) {
            schema.getSchemaAppliedDirective('audit') == null ? schema : schema.transform { it.description('audited') }
        }
    }

    static class UpperWiring implements SchemaDirectiveWiring {
        @Override
        GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            DataFetcher<?> original = environment.fieldDataFetcher
            environment.setFieldDataFetcher({ fetching -> original.get(fetching).toString().toUpperCase() } as DataFetcher)
        }
    }

    static class TaggedWiring implements SchemaDirectiveWiring {
        @Override
        GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            if (!environment.containsDirective('tagged')) {
                return environment.element
            }
            String label = environment.getAppliedDirective('tagged').getArgument('label').getValue()
            DataFetcher<?> original = environment.fieldDataFetcher
            environment.setFieldDataFetcher({ fetching -> "[${label}] ${original.get(fetching)}".toString() } as DataFetcher)
        }
    }
}
