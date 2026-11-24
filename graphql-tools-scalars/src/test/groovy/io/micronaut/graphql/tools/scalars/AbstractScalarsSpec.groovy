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
package io.micronaut.graphql.tools.scalars

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

abstract class AbstractScalarsSpec extends Specification {

    protected ApplicationContext applicationContext

    protected void startContext(String schema, String specName, Map config = [:]) {
        def builder = ApplicationContext.builder(
                ['spec.name': specName, 'micronaut.graphql.tools.manifest.enabled': true] << config, 'test')
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry()
        registry.merge(new SchemaParser().parse(schema))
        builder.singletons(registry)
        applicationContext = builder.start()
    }

    protected GraphQL getGraphQL() {
        applicationContext.getBean(GraphQL)
    }

    protected ExecutionResult execute(String query, Map variables = [:]) {
        graphQL.execute(ExecutionInput.newExecutionInput().query(query).variables(variables).build())
    }

    void cleanup() {
        applicationContext?.close()
    }

}
