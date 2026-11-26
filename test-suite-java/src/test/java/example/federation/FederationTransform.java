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

import graphql.GraphQLContext;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.graphql.tools.annotation.GraphQLType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static graphql.Scalars.GraphQLString;

// tag::transform[]
public final class FederationTransform {

    private static final int MAX_REPRESENTATIONS = 100;

    public static final String FORBIDDEN_CODE = "FORBIDDEN";

    private FederationTransform() {
    }

    /**
     * An entity type: how to resolve a representation, and who may. Both are required, so that no entity is served without a
     * decision about its callers.
     *
     * @param resolver turns a representation into the entity
     * @param access   decides from the context of the execution whether the caller may read the entity
     */
    public record Entity(Function<Map<String, Object>, Object> resolver, Predicate<GraphQLContext> access) {
    }

    private static DataFetcherResult<Object> forbidden(DataFetchingEnvironment environment, String what) {
        return DataFetcherResult.newResult()
                .error(GraphqlErrorBuilder.newError(environment).message("Access to " + what + " is forbidden")
                        .extensions(Map.of("code", FORBIDDEN_CODE)).build())
                .build();
    }

    private static String typeName(Class<?> type) {
        @SuppressWarnings("unchecked")
        Optional<BeanIntrospection<Object>> introspection = BeanIntrospector.SHARED.findIntrospection((Class<Object>) type);
        return introspection.flatMap(i -> i.stringValue(GraphQLType.class, "name")).filter(name -> !name.isEmpty())
                .orElse(type.getSimpleName());
    }

    public static GraphQLSchema apply(GraphQLSchema schema, Map<String, Entity> entities, Predicate<GraphQLContext> serviceAccess) {
        String sdl = new SchemaPrinter(SchemaPrinter.Options.defaultOptions()).print(schema);
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
            List<Object> representations = environment.getArgument("representations");
            if (representations.size() > MAX_REPRESENTATIONS) {
                throw new IllegalArgumentException("At most " + MAX_REPRESENTATIONS + " representations");
            }
            List<Entity> targets = new ArrayList<>();
            for (Object representation : representations) {
                if (!(representation instanceof Map<?, ?> map) || !(map.get("__typename") instanceof String typeName)) {
                    throw new IllegalArgumentException("A representation needs a __typename");
                }
                Entity target = entities.get(typeName);
                if (target == null) {
                    throw new IllegalArgumentException("No entity type " + typeName);
                }
                if (!target.access().test(environment.getGraphQlContext())) {
                    return forbidden(environment, "the entity " + typeName);
                }
                targets.add(target);
            }
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < targets.size(); i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> key = (Map<String, Object>) representations.get(i);
                result.add(targets.get(i).resolver().apply(key));
            }
            return result;
        };
        TypeResolver typeResolver = environment -> {
            Object object = environment.getObject();
            String name = object instanceof Map<?, ?> map ? (String) map.get("__typename") : typeName(object.getClass());
            return environment.getSchema().getObjectType(name);
        };
        GraphQLCodeRegistry registry = schema.getCodeRegistry().transform(builder -> builder
                .dataFetcher(FieldCoordinates.coordinates(query.getName(), "_service"),
                        (DataFetcher<Object>) environment -> serviceAccess.test(environment.getGraphQlContext())
                                ? Map.of("sdl", sdl) : forbidden(environment, "_service"))
                .dataFetcher(FieldCoordinates.coordinates(query.getName(), "_entities"), entitiesFetcher)
                .typeResolver("_Entity", typeResolver));
        return schema.transform(builder -> builder.query(query).codeRegistry(registry).additionalType(entity));
    }

}
// end::transform[]
