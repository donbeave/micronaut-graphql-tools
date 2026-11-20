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
package example.tck

import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping
import jakarta.inject.Singleton
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

// The fixtures of the TCK scenario `constraint-violations`: resolvers with constrained parameters and constrained input
// classes, validated by a real Micronaut Validation run (#468).

// tag::constrained-input[]
@GraphQLInput
class Line {
    @field:NotBlank
    var sku: String = ""

    @field:Positive
    var quantity: Int = 0
}

@GraphQLInput
class Cart {
    @field:NotBlank
    var owner: String = ""

    @field:Positive
    var quantity: Int = 0

    @field:Valid
    var lines: List<Line> = emptyList()
}
// end::constrained-input[]

@Factory
@Requires(property = "tck.scenario", value = "constraint-violations")
class ConstraintViolationsScalars {

    @Bean
    @Singleton
    fun limits(): GraphQLScalarMapping =
        GraphQLScalarMapping.builder(
            GraphQLScalarType.newScalar().name("Limits").coercing(object : Coercing<Any, Any> {
                override fun serialize(value: Any): Any = value

                override fun parseValue(value: Any): Any = value

                override fun parseLiteral(value: Any): Any = throw UnsupportedOperationException()
            }).build()
        ).outputClasses(Map::class.java).inputClasses(Map::class.java).build()
}

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "constraint-violations")
open class ConstraintViolationsQueries {

    fun fine(): String = "fine"

    // tag::constrained-parameter[]
    open fun rename(@NotBlank name: String): String = name

    open fun checkout(@Valid cart: Cart): String = cart.owner
    // end::constrained-parameter[]

    open fun restock(@Valid lines: List<Line>): String = lines.size.toString()

    open fun cap(limits: Map<String, @Positive Int>): String = limits.toString()

    open fun several(@NotBlank name: String, @Positive count: Int, @Valid cart: Cart): String = name
}
