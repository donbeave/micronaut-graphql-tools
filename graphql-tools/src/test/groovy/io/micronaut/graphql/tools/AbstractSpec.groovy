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
package io.micronaut.graphql.tools

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import spock.lang.Specification

abstract class AbstractSpec extends Specification {

    protected ApplicationContext applicationContext

    protected void startContext(@Nullable String graphQlSchema, @Nullable String specName = null, Map additionalConfig = [:]) {
        Map defaults = lazyBinding() ? ["micronaut.graphql.tools.eager-init": false] : [:]
        def ctxBuilder = ApplicationContext
                .builder(
                        ["spec.name": specName ?: getClass().simpleName,
                         "micronaut.graphql.tools.manifest.enabled": true] << defaults << additionalConfig,
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
