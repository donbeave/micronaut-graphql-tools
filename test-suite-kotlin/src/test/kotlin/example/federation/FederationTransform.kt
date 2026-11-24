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
package example.federation

import graphql.Scalars.GraphQLString
import graphql.scalars.ExtendedScalars
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull.nonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLUnionType
import graphql.schema.TypeResolver
import graphql.schema.idl.SchemaPrinter

// tag::transform[]
object FederationTransform {

    fun apply(schema: GraphQLSchema, entities: Map<String, (Map<String, Any?>) -> Any>): GraphQLSchema {
        val sdl = SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeSchemaDefinition(false)).print(schema)
        val any = GraphQLScalarType.newScalar(ExtendedScalars.Json).name("_Any").build()
        val entity = GraphQLUnionType.newUnionType().name("_Entity")
            .possibleTypes(
                *schema.allTypesAsList.filterIsInstance<GraphQLObjectType>()
                    .filter { it.hasAppliedDirective("key") }.toTypedArray()
            )
            .build()
        val service = GraphQLObjectType.newObject().name("_Service")
            .field(GraphQLFieldDefinition.newFieldDefinition().name("sdl").type(nonNull(GraphQLString)))
            .build()
        val query = schema.queryType.transform { builder ->
            builder
                .field(GraphQLFieldDefinition.newFieldDefinition().name("_service").type(nonNull(service)))
                .field(
                    GraphQLFieldDefinition.newFieldDefinition().name("_entities")
                        .argument(
                            GraphQLArgument.newArgument().name("representations")
                                .type(nonNull(GraphQLList.list(nonNull(any))))
                        )
                        .type(nonNull(GraphQLList.list(entity)))
                )
        }
        val entitiesFetcher = DataFetcher<Any> { environment ->
            val representations = environment.getArgument<List<Map<String, Any?>>>("representations")!!
            representations.map { representation ->
                val resolver = entities[representation["__typename"] as String]
                    ?: throw IllegalArgumentException("No entity type ${representation["__typename"]}")
                resolver(representation)
            }
        }
        val typeResolver = TypeResolver { environment ->
            val obj = environment.getObject<Any>()
            val name = if (obj is Map<*, *>) obj["__typename"] as String else obj.javaClass.simpleName
            environment.schema.getObjectType(name)
        }
        val registry = schema.codeRegistry.transform { builder ->
            builder
                .dataFetcher(FieldCoordinates.coordinates(query.name, "_service"), DataFetcher<Any> { mapOf("sdl" to sdl) })
                .dataFetcher(FieldCoordinates.coordinates(query.name, "_entities"), entitiesFetcher)
                .typeResolver("_Entity", typeResolver)
        }
        return schema.transform { builder -> builder.query(query).codeRegistry(registry).additionalType(entity) }
    }
}
// end::transform[]
