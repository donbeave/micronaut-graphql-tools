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
package io.micronaut.graphql.tools.benchmarks;

import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeRuntimeWiring;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The schema of the execution-hop benchmarks, on plain graphql-java: an object has ten property fields, read by
 * graphql-java's property fetcher, and one method field. A method field is the kind of fetcher the library binds to a
 * resolver method, so it is the one a per-field hop wraps.
 */
final class HopSchema {

    static final int PROPERTY_FIELDS = 10;
    static final int ITEMS = 1_000;

    static final String ROOT_QUERY = "{ root { " + selection() + " } }";
    static final String ITEMS_QUERY = "{ items { " + selection() + " } }";
    static final String ASYNC_QUERY = "{ asyncRoot { " + selection() + " } }";

    record Node(String p0, String p1, String p2, String p3, String p4, String p5, String p6, String p7, String p8,
                String p9) {

        String method() {
            return "method";
        }
    }

    static final Node NODE = new Node("v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8", "v9");
    static final List<Node> NODES = Collections.nCopies(ITEMS, NODE);

    /** A method fetcher: it may not throw, so that a per-field hop is one {@code supplyAsync}. */
    interface Method extends Function<DataFetchingEnvironment, Object> {
    }

    private HopSchema() {
    }

    private static String selection() {
        StringBuilder fields = new StringBuilder();
        for (int i = 0; i < PROPERTY_FIELDS; i++) {
            fields.append('p').append(i).append(' ');
        }
        return fields.append("method").toString();
    }

    private static String sdl() {
        StringBuilder sdl = new StringBuilder("type Query { root: Node! items: [Node!]! asyncRoot: Node! }\ntype Node {\n");
        for (int i = 0; i < PROPERTY_FIELDS; i++) {
            sdl.append("  p").append(i).append(": String!\n");
        }
        return sdl.append("  method: String!\n}\n").toString();
    }

    /**
     * Builds the executable schema. {@code queryMethods} are the method fetchers of {@code Query}, by field name;
     * {@code wrap} turns a method fetcher into the fetcher that is registered (the per-field hop). With a
     * {@code counter} every fetcher reports to it before it runs, so that the counter sees where it ran.
     */
    static GraphQL build(Map<String, Method> queryMethods, Function<Method, DataFetcher<?>> wrap, ThreadChanges counter) {
        Function<Method, Method> observed = method -> counter == null ? method : env -> {
            counter.fetch();
            return method.apply(env);
        };
        TypeRuntimeWiring.Builder query = TypeRuntimeWiring.newTypeWiring("Query");
        queryMethods.forEach((name, method) -> query.dataFetcher(name, wrap.apply(observed.apply(method))));
        TypeRuntimeWiring.Builder node = TypeRuntimeWiring.newTypeWiring("Node");
        for (int i = 0; i < PROPERTY_FIELDS; i++) {
            DataFetcher<Object> property = PropertyDataFetcher.fetching("p" + i);
            node.dataFetcher("p" + i, counter == null ? property : env -> {
                counter.fetch();
                return property.get(env);
            });
        }
        node.dataFetcher("method", wrap.apply(observed.apply(env -> ((Node) env.getSource()).method())));
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring().type(query).type(node).build();
        return GraphQL.newGraphQL(new SchemaGenerator().makeExecutableSchema(new SchemaParser().parse(sdl()), wiring))
                .build();
    }
}
