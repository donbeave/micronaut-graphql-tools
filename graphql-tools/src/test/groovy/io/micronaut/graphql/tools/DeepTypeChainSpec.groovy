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

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration
import io.micronaut.graphql.tools.manifest.BindingManifest
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources
import io.micronaut.graphql.tools.binding.internal.schema.TypeExtensions
import io.micronaut.graphql.tools.internal.model.GraphQLBeanIntrospectionRegistry
import io.micronaut.graphql.tools.internal.model.GraphQLResolversRegistry
import io.micronaut.graphql.tools.internal.wiring.GraphQLRuntimeWiringGenerator

/**
 * A schema that is a chain of thousands of types binds on the default thread stack: the walk from a field to the types
 * it returns, and from an input field to its input type, is not one recursive call per type.
 */
class DeepTypeChainSpec extends AbstractTest {

    static final String SPEC_NAME = "DeepTypeChainSpec"

    // graphql-java builds the executable schema with one recursive call per type, and a chain of a thousand types
    // overflows the default stack of a cold JVM, so the application starts with a chain it can build
    void "start with a chain of 200 object types and 200 input types"() {
        when:
        startContext(chainSchema(200), SPEC_NAME, ["micronaut.graphql.tools.strict": true])
        getGraphQLBean()

        then:
        noExceptionThrown()
        applicationContext.getBean(BindingManifest).lines.any { it.contains("Node199") }

        and:
        executeQuery("{ root { name next { name } } }").data == [root: [name: "n", next: [name: "n"]]]
        executeQuery('{ echo(input: {name: "a", next: {name: "b"}}) }').data == [echo: "a>b"]
    }

    // the binding of the longer chains is run without graphql-java's schema build, which recurses per type
    void "bind a chain of #types object types and #types input types"() {
        given:
        startContext(chainSchema(types), SPEC_NAME, ["micronaut.graphql.tools.strict": true])
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry().merge(new SchemaParser().parse(chainSchema(types)))
        SchemaSources.addImplicitRoots(registry)
        def generator = new GraphQLRuntimeWiringGenerator(applicationContext, new GraphQLBeanIntrospectionRegistry(),
                applicationContext.getBean(GraphQLResolversRegistry), TypeExtensions.merge(registry),
                new SchemaMappingDictionary(), applicationContext.getBean(GraphQLToolsConfiguration),
                TypeExtensions.objectExtensionFields(registry))

        when:
        generator.generate([])
        def manifest = generator.manifestRecorder.build(registry)

        then:
        noExceptionThrown()
        manifest.lines.any { it.contains("Node${types - 1}") }

        where:
        types << [1000, 5000]
    }

    private static String chainSchema(int types) {
        StringBuilder sdl = new StringBuilder("schema { query: Query }\ntype Query { root: Node0 echo(input: In0!): String! }\n")
        for (int i = 0; i < types; i++) {
            String next = i + 1 < types ? "Node${i + 1}" : "Node0"
            String nextInput = i + 1 < types ? "In${i + 1}" : "In0"
            sdl << "type Node$i { name: String! next: $next }\n"
            sdl << "input In$i { name: String! next: $nextInput }\n"
        }
        sdl.toString()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        Node root() {
            new Node(name: "n", next: new Node(name: "n"))
        }

        String echo(In input) {
            input.name + (input.next == null ? "" : ">" + input.next.name)
        }
    }

    @GraphQLType
    static class Node {
        String name
        Node next
    }

    @GraphQLInput
    static class In {
        String name
        In next
    }

}
