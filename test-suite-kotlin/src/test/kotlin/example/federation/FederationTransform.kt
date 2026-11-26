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

import graphql.ErrorClassification
import graphql.ErrorType
import graphql.GraphQLContext
import graphql.GraphQLError
import graphql.Scalars.GraphQLString
import graphql.execution.DataFetcherResult
import graphql.language.SourceLocation
import graphql.scalars.ExtendedScalars
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
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
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.graphql.tools.annotation.GraphQLType

// tag::transform[]
object FederationTransform {

    private const val MAX_REPRESENTATIONS = 100

    const val FORBIDDEN_CODE = "FORBIDDEN"

    /**
     * An entity type: how to resolve a representation, and who may. Both are required, so that no entity is served without a
     * decision about its callers.
     */
    class Entity(
        val resolver: (Map<String, Any?>) -> Any,
        val access: (GraphQLContext) -> Boolean
    )

    // GraphqlErrorBuilder is not used: its self-referencing generics overflow the Kotlin compiler on graphql-java 26.2.
    private class ForbiddenError(private val environment: DataFetchingEnvironment, private val text: String) : GraphQLError {
        override fun getMessage(): String = text

        override fun getLocations(): List<SourceLocation>? = listOfNotNull(environment.field.sourceLocation)

        override fun getPath(): List<Any> = environment.executionStepInfo.path.toList()

        override fun getErrorType(): ErrorClassification = ErrorType.DataFetchingException

        override fun getExtensions(): Map<String, Any> = mapOf("code" to FORBIDDEN_CODE)
    }

    private fun forbidden(environment: DataFetchingEnvironment, what: String): Any =
        DataFetcherResult.newResult<Any>().error(ForbiddenError(environment, "Access to $what is forbidden")).build()

    private fun typeName(type: Class<*>): String =
        BeanIntrospector.SHARED.findIntrospection(type).flatMap { it.stringValue(GraphQLType::class.java, "name") }
            .filter { it.isNotEmpty() }.orElse(type.simpleName)

    fun apply(schema: GraphQLSchema, entities: Map<String, Entity>, serviceAccess: (GraphQLContext) -> Boolean): GraphQLSchema {
        val sdl = SchemaPrinter(SchemaPrinter.Options.defaultOptions()).print(schema)
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
            val representations = environment.getArgument<List<Any?>>("representations")!!
            require(representations.size <= MAX_REPRESENTATIONS) { "At most $MAX_REPRESENTATIONS representations" }
            val targets = ArrayList<Entity>()
            for (representation in representations) {
                val typeName = (representation as? Map<*, *>)?.get("__typename") as? String
                    ?: throw IllegalArgumentException("A representation needs a __typename")
                val target = entities[typeName] ?: throw IllegalArgumentException("No entity type $typeName")
                if (!target.access(environment.graphQlContext)) {
                    return@DataFetcher forbidden(environment, "the entity $typeName")
                }
                targets.add(target)
            }
            targets.mapIndexed { index, target ->
                @Suppress("UNCHECKED_CAST")
                target.resolver(representations[index] as Map<String, Any?>)
            }
        }
        val typeResolver = TypeResolver { environment ->
            val obj = environment.getObject<Any>()!!
            val name = if (obj is Map<*, *>) obj["__typename"] as String else typeName(obj.javaClass)
            environment.schema.getObjectType(name)
        }
        val registry = schema.codeRegistry.transform { builder ->
            builder
                .dataFetcher(FieldCoordinates.coordinates(query.name, "_service"), DataFetcher<Any> { environment ->
                    if (serviceAccess(environment.graphQlContext)) mapOf("sdl" to sdl) else forbidden(environment, "_service")
                })
                .dataFetcher(FieldCoordinates.coordinates(query.name, "_entities"), entitiesFetcher)
                .typeResolver("_Entity", typeResolver)
        }
        return schema.transform { builder -> builder.query(query).codeRegistry(registry).additionalType(entity) }
    }
}
// end::transform[]
