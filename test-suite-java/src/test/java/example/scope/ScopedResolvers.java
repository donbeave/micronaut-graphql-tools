package example.scope;

import graphql.language.Definition;
import graphql.schema.idl.SchemaParser;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.TypeDefinitionRegistryCustomizer;
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
    static class ExtendQuery implements TypeDefinitionRegistryCustomizer {
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
