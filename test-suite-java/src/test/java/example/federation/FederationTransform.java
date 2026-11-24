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
package example.federation;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;
import graphql.schema.TypeResolver;
import graphql.schema.idl.SchemaPrinter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static graphql.Scalars.GraphQLString;

// tag::transform[]
public final class FederationTransform {

    private FederationTransform() {
    }

    public static GraphQLSchema apply(GraphQLSchema schema, Map<String, Function<Map<String, Object>, Object>> entities) {
        String sdl = new SchemaPrinter(SchemaPrinter.Options.defaultOptions()
                .includeSchemaDefinition(false)).print(schema);
        GraphQLScalarType any = GraphQLScalarType.newScalar(ExtendedScalars.Json).name("_Any").build();
        GraphQLUnionType entity = GraphQLUnionType.newUnionType().name("_Entity")
                .possibleTypes(schema.getAllTypesAsList().stream()
                        .filter(t -> t instanceof GraphQLObjectType && ((GraphQLObjectType) t).hasAppliedDirective("key"))
                        .map(t -> (GraphQLObjectType) t).toArray(GraphQLObjectType[]::new))
                .build();
        GraphQLObjectType service = GraphQLObjectType.newObject().name("_Service")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("sdl").type(GraphQLNonNull.nonNull(GraphQLString)))
                .build();
        GraphQLObjectType query = schema.getQueryType().transform(builder -> builder
                .field(GraphQLFieldDefinition.newFieldDefinition().name("_service").type(GraphQLNonNull.nonNull(service)))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("_entities")
                        .argument(GraphQLArgument.newArgument().name("representations")
                                .type(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(any)))))
                        .type(GraphQLNonNull.nonNull(GraphQLList.list(entity)))));
        DataFetcher<Object> entitiesFetcher = environment -> {
            List<Map<String, Object>> representations = environment.getArgument("representations");
            return representations.stream().map(representation -> {
                Function<Map<String, Object>, Object> resolver = entities.get((String) representation.get("__typename"));
                if (resolver == null) {
                    throw new IllegalArgumentException("No entity type " + representation.get("__typename"));
                }
                return resolver.apply(representation);
            }).toList();
        };
        TypeResolver typeResolver = environment -> {
            Object object = environment.getObject();
            String name = object instanceof Map<?, ?> map ? (String) map.get("__typename") : object.getClass().getSimpleName();
            return environment.getSchema().getObjectType(name);
        };
        GraphQLCodeRegistry registry = schema.getCodeRegistry().transform(builder -> builder
                .dataFetcher(FieldCoordinates.coordinates(query.getName(), "_service"),
                        (DataFetcher<Object>) environment -> Map.of("sdl", sdl))
                .dataFetcher(FieldCoordinates.coordinates(query.getName(), "_entities"), entitiesFetcher)
                .typeResolver("_Entity", typeResolver));
        return schema.transform(builder -> builder.query(query).codeRegistry(registry).additionalType(entity));
    }

}
// end::transform[]
