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
package example.dataclass

import graphql.schema.idl.SchemaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.input.ArgumentValue
import jakarta.inject.Singleton

// Kotlin data classes as models, inputs and parameters (#180).

const val DATA_CLASSES = "data-classes"

@Factory
class DataClassFixtures {

    @Bean
    @Singleton
    @Requires(env = [DATA_CLASSES])
    fun schema() = TypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                """
                type Account { name: String! nickname: String level: Int! tags: [String!]! }
                input AccountInput { name: String! nickname: String level: Int tags: [String!] }
                input DraftInput { title: String! summary: String note: String }
                input SettingsInput { theme: String! owner: AccountInput }
                extend type Query {
                    account: Account!
                    describe(input: AccountInput!): String!
                    describeList(inputs: [AccountInput!]!): [String!]!
                    draft(input: DraftInput!): String!
                    settings(input: SettingsInput!): String!
                    copyOf(input: AccountInput!): Account!
                }
                """.trimIndent()
            )
        )
    }
}

@GraphQLType
data class Account(val name: String, val nickname: String? = null, val level: Int = 1, val tags: List<String> = emptyList())

@GraphQLInput
data class AccountInput(
    val name: String,
    val nickname: String? = "anonymous",
    val level: Int? = 1,
    val tags: List<String> = listOf("new"),
)

@GraphQLInput
data class DraftInput(
    val title: String,
    val summary: ArgumentValue<String> = ArgumentValue.omitted(),
    val note: String? = "none",
)

@GraphQLInput
data class SettingsInput(val theme: String = "light", val owner: AccountInput? = null)

@Singleton
@Requires(env = [DATA_CLASSES])
@GraphQLRootResolver
class DataClassQuery {

    fun account(): Account = Account("Ada", level = 3)

    fun describe(input: AccountInput): String = input.toString()

    fun describeList(inputs: List<AccountInput>): List<String> = inputs.map { it.toString() }

    fun draft(input: DraftInput): String = when {
        input.summary.isOmitted -> "omitted"
        input.summary.isNull -> "null"
        else -> "present:" + input.summary.get()
    } + " note=" + input.note

    fun settings(input: SettingsInput): String = input.toString()

    fun copyOf(input: AccountInput): Account = Account(input.name, input.nickname, (input.level ?: 0) + 1, input.tags)
}
