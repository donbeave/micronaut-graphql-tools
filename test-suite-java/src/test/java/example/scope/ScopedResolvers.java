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
package example.scope;

import graphql.language.Definition;
import graphql.schema.idl.SchemaParser;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.runtime.http.scope.RequestScope;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

final class ScopedResolvers {

    static final String ENVIRONMENT = "resolver-scopes";

    private static final AtomicInteger IDS = new AtomicInteger();

    private ScopedResolvers() {
    }

    @Singleton
    @Requires(env = ENVIRONMENT)
    static class ExtendQuery implements GraphQLTypeDefinitionRegistryCustomizer {
        @Override
        public void customize(graphql.schema.idl.TypeDefinitionRegistry registry) {
            registry.merge(new SchemaParser().parse(
                    "extend type Query { singletonId: Int! prototypeId: Int! requestId: Int! }"));
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(env = ENVIRONMENT)
    static class SingletonResolver {
        private final int id = IDS.incrementAndGet();

        public int singletonId() {
            return id;
        }
    }

    @Prototype
    @GraphQLRootResolver
    @Requires(env = ENVIRONMENT)
    static class PrototypeResolver {
        private final int id = IDS.incrementAndGet();

        public int prototypeId() {
            return id;
        }
    }

    @RequestScope
    @GraphQLRootResolver
    @Requires(env = ENVIRONMENT)
    static class RequestResolver {
        private final int id = IDS.incrementAndGet();

        public int requestId() {
            return id;
        }
    }

}
