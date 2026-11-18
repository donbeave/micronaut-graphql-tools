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

import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.GraphQLSchemaCustomizer;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
public abstract class AbstractEagerInitState {

    static final String QUERY = "{ root { id next { name computed(n: 1) } } }";

    static final int FIELDS = 1600;

    @Param({"true", "false"})
    public boolean eagerInit;

    final AtomicInteger bindings = new AtomicInteger();

    private TypeDefinitionRegistry registry;

    @Setup
    public void parse() {
        registry = LargeSchema.registry(FIELDS);
    }

    ApplicationContext start() {
        bindings.set(0);
        GraphQLSchemaCustomizer counting = schema -> {
            bindings.incrementAndGet();
            return schema;
        };
        return ApplicationContext.builder()
                .properties(Map.of(
                        "graphql.graphql-ws.enabled", false,
                        "graphql.graphiql.enabled", false,
                        "benchmarks.large", true,
                        LargeSchema.FIELDS, FIELDS,
                        "micronaut.graphql.tools.eager-init", eagerInit))
                .singletons(registry, counting)
                .start();
    }

    void checkBoundAtStart() {
        int expected = eagerInit ? 1 : 0;
        if (bindings.get() != expected) {
            throw new IllegalStateException("eager-init " + eagerInit + ": " + bindings.get()
                    + " bindings after the start, expected " + expected);
        }
    }

}
