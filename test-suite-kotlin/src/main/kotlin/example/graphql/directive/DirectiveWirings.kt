package example.graphql.directive

import graphql.schema.DataFetcher
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.graphql.tools.GraphQLSchemaCustomizer
import io.micronaut.graphql.tools.SchemaDirective
import jakarta.inject.Singleton

@Factory
class DirectiveWirings {

    @Bean
    @Singleton
    fun upper(): SchemaDirective = SchemaDirective.named("upper", UpperWiring())

    @Bean
    @Singleton
    fun tagged(): SchemaDirective = SchemaDirective.everyElement(listOf("tagged"), TaggedWiring())

    @Bean
    @Singleton
    fun audit() = GraphQLSchemaCustomizer { schema ->
        if (schema.getSchemaAppliedDirective("audit") == null) schema
        else schema.transform { builder -> builder.description("audited") }
    }

    class UpperWiring : SchemaDirectiveWiring {
        override fun onField(environment: SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition>): GraphQLFieldDefinition {
            val original = environment.fieldDataFetcher
            return environment.setFieldDataFetcher(DataFetcher { fetching -> original.get(fetching).toString().uppercase() })
        }
    }

    class TaggedWiring : SchemaDirectiveWiring {
        override fun onField(environment: SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition>): GraphQLFieldDefinition {
            if (!environment.containsDirective("tagged")) {
                return environment.element
            }
            val label = environment.getAppliedDirective("tagged").getArgument("label")!!.getValue<String>()
            val original = environment.fieldDataFetcher
            return environment.setFieldDataFetcher(DataFetcher { fetching -> "[$label] ${original.get(fetching)}" })
        }
    }
}
