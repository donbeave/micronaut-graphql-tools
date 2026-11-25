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

import graphql.Scalars
import graphql.scalars.ExtendedScalars
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLUnionType
import graphql.schema.TypeResolver
import graphql.schema.idl.SchemaPrinter
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.graphql.tools.annotation.GraphQLType
import groovy.transform.CompileStatic

// tag::transform[]
@CompileStatic
final class FederationTransform {

    private static final int MAX_REPRESENTATIONS = 100

    private FederationTransform() {
    }

    private static String typeName(Class<?> type) {
        String name = BeanIntrospector.SHARED.findIntrospection((Class<Object>) type)
                .flatMap { it.stringValue(GraphQLType, 'name') }.orElse('')
        return name.empty ? type.simpleName : name
    }

    static GraphQLSchema apply(GraphQLSchema schema, Map<String, Closure<Object>> entities) {
        String sdl = new SchemaPrinter(SchemaPrinter.Options.defaultOptions()).print(schema)
        GraphQLScalarType any = GraphQLScalarType.newScalar(ExtendedScalars.Json).name('_Any').build()
        GraphQLObjectType[] keyed = schema.allTypesAsList
                .findAll { it instanceof GraphQLObjectType && ((GraphQLObjectType) it).hasAppliedDirective('key') }
                .collect { (GraphQLObjectType) it } as GraphQLObjectType[]
        GraphQLUnionType entity = GraphQLUnionType.newUnionType().name('_Entity').possibleTypes(keyed).build()
        GraphQLObjectType service = GraphQLObjectType.newObject().name('_Service')
                .field(GraphQLFieldDefinition.newFieldDefinition().name('sdl').type(GraphQLNonNull.nonNull(Scalars.GraphQLString)))
                .build()
        GraphQLObjectType query = schema.queryType.transform { GraphQLObjectType.Builder builder ->
            builder.field(GraphQLFieldDefinition.newFieldDefinition().name('_service').type(GraphQLNonNull.nonNull(service)))
            builder.field(GraphQLFieldDefinition.newFieldDefinition().name('_entities')
                    .argument(GraphQLArgument.newArgument().name('representations')
                            .type(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(any)))))
                    .type(GraphQLNonNull.nonNull(GraphQLList.list(entity))))
        }
        DataFetcher<Object> entitiesFetcher = { env ->
            List<Object> representations = env.getArgument('representations')
            if (representations.size() > MAX_REPRESENTATIONS) {
                throw new IllegalArgumentException("At most ${MAX_REPRESENTATIONS} representations".toString())
            }
            return representations.collect { Object representation ->
                if (!(representation instanceof Map) || !(((Map) representation).get('__typename') instanceof String)) {
                    throw new IllegalArgumentException('A representation needs a __typename')
                }
                String typeName = ((Map) representation).get('__typename')
                Closure<Object> resolver = entities[typeName]
                if (resolver == null) {
                    throw new IllegalArgumentException("No entity type ${typeName}".toString())
                }
                resolver.call(representation)
            }
        } as DataFetcher<Object>
        TypeResolver typeResolver = { env ->
            Object object = env.getObject()
            String name = object instanceof Map ? ((Map) object).get('__typename') : typeName(object.getClass())
            return env.schema.getObjectType(name)
        } as TypeResolver
        def registry = schema.codeRegistry.transform { builder ->
            builder.dataFetcher(FieldCoordinates.coordinates(query.name, '_service'),
                    { env -> [sdl: sdl] } as DataFetcher<Object>)
                    .dataFetcher(FieldCoordinates.coordinates(query.name, '_entities'), entitiesFetcher)
                    .typeResolver('_Entity', typeResolver)
        }
        return schema.transform { builder -> builder.query(query).codeRegistry(registry).additionalType(entity) }
    }

}
// end::transform[]
