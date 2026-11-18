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

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import jakarta.inject.Singleton

const val DEEP_TYPE_CHAIN_ENVIRONMENT = "deep-type-chain"

const val DEEP_TYPE_CHAIN_TYPES = 200

// A chain of types that extends Query. One Kotlin class backs every type of the chain: it is the length of the chain of
// schema types that matters.
@Singleton
@Requires(env = [DEEP_TYPE_CHAIN_ENVIRONMENT])
class DeepTypeChainSchema : TypeDefinitionRegistryCustomizer {
    override fun customize(registry: TypeDefinitionRegistry) {
        val sdl = StringBuilder("extend type Query { chainRoot: Node0 chainEcho(input: In0!): String! }\n")
        for (i in 0 until DEEP_TYPE_CHAIN_TYPES) {
            val next = (i + 1) % DEEP_TYPE_CHAIN_TYPES
            sdl.append("type Node$i { name: String! next: Node$next }\n")
            sdl.append("input In$i { name: String! next: In$next }\n")
        }
        registry.merge(SchemaParser().parse(sdl.toString()))
    }
}

@GraphQLRootResolver
@Requires(env = [DEEP_TYPE_CHAIN_ENVIRONMENT])
class DeepTypeChainQuery {
    fun chainRoot(): Node = Node().apply {
        name = "n"
        next = Node().apply { name = "n" }
    }

    fun chainEcho(input: In): String = input.name + (input.next?.let { ">" + it.name } ?: "")
}

@GraphQLType
@Introspected
class Node {
    var name: String = ""
    var next: Node? = null
}

@GraphQLInput
@Introspected
class In {
    var name: String = ""
    var next: In? = null
}
