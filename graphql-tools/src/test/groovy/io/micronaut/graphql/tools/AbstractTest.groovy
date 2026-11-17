package io.micronaut.graphql.tools

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import spock.lang.Specification

abstract class AbstractTest extends Specification {

    protected ApplicationContext applicationContext

    protected void startContext(@Nullable String graphQlSchema, @Nullable String specName = null, Map additionalConfig = [:]) {
        Map defaults = lazyBinding() ? ["micronaut.graphql.tools.eager-init": false] : [:]
        def ctxBuilder = ApplicationContext
                .builder(
                        ["spec.name": specName ?: getClass().simpleName,
                         "micronaut.graphql.tools.manifest.record": true] << defaults << additionalConfig,
                        "test"
                )

        if (graphQlSchema != null) {
            TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry()
            typeRegistry.merge(new SchemaParser().parse(graphQlSchema))
            ctxBuilder.singletons(typeRegistry)
        }

        applicationContext = ctxBuilder.start()
    }

    protected boolean lazyBinding() {
        true
    }

    GraphQL getGraphQLBean() {
        applicationContext.getBean(GraphQL.class)
    }

    ExecutionResult executeQuery(@NonNull String query) {
        getGraphQLBean().execute(query.trim())
    }

    void cleanup() {
        applicationContext?.close()
    }

}
