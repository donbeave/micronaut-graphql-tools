/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.graphql.directive

import graphql.schema.DataFetcher
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.graphql.tools.customizer.GraphQLSchemaCustomizer
import io.micronaut.graphql.tools.directive.SchemaDirective
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
