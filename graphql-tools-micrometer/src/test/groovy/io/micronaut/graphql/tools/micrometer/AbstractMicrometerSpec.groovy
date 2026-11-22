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
package io.micronaut.graphql.tools.micrometer

import graphql.ExecutionResult
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.ApplicationContext
import reactor.core.publisher.Mono
import spock.lang.Specification

abstract class AbstractMicrometerSpec extends Specification {

    static final String SCHEMA = """
type Query {
  hello: String
  broken: String
  book: Book
  loaded: String
  timed: String
}

type Book {
  title: String
}
"""

    ApplicationContext context
    SimpleMeterRegistry meters

    protected void start(String specName, Map config = [:], boolean metrics = true) {
        meters = new SimpleMeterRegistry()
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry()
        registry.merge(new SchemaParser().parse(SCHEMA))
        def builder = ApplicationContext.builder(
                ['spec.name'                                      : specName,
                 'graphql.graphql-ws.enabled'                     : false,
                 'graphql.graphiql.enabled'                       : false,
                 'micronaut.graphql.tools.execution.executor'     : 'none',
                 'micronaut.metrics.enabled'                      : metrics] << config, 'test')
                .singletons(registry)
        if (metrics) {
            builder.singletons(meters)
        }
        context = builder.start()
    }

    protected ExecutionResult run(String query, String operationName = null) {
        Mono.from(context.getBean(GraphQLInvocation).invoke(new GraphQLInvocationData(query, operationName, [:]), null, null)).block()
    }

    protected boolean hasBean(String simpleName) {
        context.containsBean(Class.forName('io.micronaut.graphql.tools.micrometer.internal.' + simpleName))
    }

    void cleanup() {
        context?.close()
    }

}
