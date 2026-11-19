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
package example.deepchain

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

// A chain of 200 object types and 200 input types is bound and queried on the default thread stack. One Groovy class
// backs every type of the chain: it is the length of the chain of schema types that matters.
@MicronautTest(environments = DeepTypeChainSpec.ENVIRONMENT)
class DeepTypeChainSpec extends Specification {

    static final String ENVIRONMENT = 'deep-type-chain'

    static final int TYPES = 200

    @Inject
    GraphQL graphQL

    void 'a chain of types is bound'() {
        when:
        ExecutionResult object = graphQL.execute('{ chainRoot { name next { name } } }')
        ExecutionResult input = graphQL.execute('{ chainEcho(input: {name: "a", next: {name: "b"}}) }')

        then:
        object.errors.isEmpty()
        object.getData() == [chainRoot: [name: 'n', next: [name: 'n']]]
        input.errors.isEmpty()
        input.getData() == [chainEcho: 'a>b']
    }

    @Singleton
    @Requires(env = DeepTypeChainSpec.ENVIRONMENT)
    static class Schema implements TypeDefinitionRegistryCustomizer {
        @Override
        void customize(TypeDefinitionRegistry registry) {
            StringBuilder sdl = new StringBuilder('extend type Query { chainRoot: Node0 chainEcho(input: In0!): String! }\n')
            for (int i = 0; i < TYPES; i++) {
                int next = (i + 1) % TYPES
                sdl << "type Node$i { name: String! next: Node$next }\n"
                sdl << "input In$i { name: String! next: In$next }\n"
            }
            registry.merge(new SchemaParser().parse(sdl.toString()))
        }
    }

    @GraphQLRootResolver
    @Requires(env = DeepTypeChainSpec.ENVIRONMENT)
    static class Query {
        Node chainRoot() {
            new Node(name: 'n', next: new Node(name: 'n'))
        }

        String chainEcho(In input) {
            input.name + (input.next == null ? '' : '>' + input.next.name)
        }
    }

    @GraphQLType
    @Introspected
    static class Node {
        String name
        Node next
    }

    @GraphQLInput
    @Introspected
    static class In {
        String name
        In next
    }

}
