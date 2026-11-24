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
import graphql.GraphQLError
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.graphql.tools.error.GraphQLExceptionContext
import io.micronaut.graphql.tools.error.GraphQLExceptionMapper
import jakarta.validation.ConstraintViolationException
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping
import jakarta.inject.Singleton
import io.micronaut.validation.validator.constraints.ConstraintValidator
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext
import jakarta.validation.Constraint
import jakarta.validation.Payload
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.valueextraction.ExtractedValue
import jakarta.validation.valueextraction.ValueExtractor
import java.util.NavigableMap
import java.util.SortedMap
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KClass

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

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class OrderedSpan(
    val message: String = "min must not exceed max",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

@GraphQLInput
@OrderedSpan
class Span {
    var min: Int = 0
    var max: Int = 0
}

@Singleton
@Requires(property = "tck.scenario", value = "constraint-violations")
class OrderedSpanValidator : ConstraintValidator<OrderedSpan, Span> {
    override fun isValid(value: Span?, annotation: AnnotationValue<OrderedSpan>, context: ConstraintValidatorContext): Boolean =
        value == null || value.min <= value.max
}

enum class Region { NORTH, SOUTH }

class Bag<T>(val items: List<T>)

@Singleton
@Requires(property = "tck.scenario", value = "constraint-violations")
class BagExtractor : ValueExtractor<Bag<@ExtractedValue Any>> {
    override fun extractValues(bag: Bag<Any>, receiver: ValueExtractor.ValueReceiver) {
        bag.items.forEachIndexed { index, item -> receiver.indexedValue("item", index, item) }
    }
}

class Tray<T>(val items: List<T>)

@Singleton
@Requires(property = "tck.scenario", value = "constraint-violations")
class TrayExtractor : ValueExtractor<Tray<@ExtractedValue Any>> {
    override fun extractValues(tray: Tray<Any>, receiver: ValueExtractor.ValueReceiver) {
        tray.items.forEachIndexed { index, item -> receiver.indexedValue("<tray item>", index, item) }
    }
}

@Factory
@Requires(property = "tck.scenario", value = "constraint-violations")
class ConstraintViolationsScalars {

    @Bean
    @Singleton
    fun limits(): GraphQLScalarMapping = scalar("Limits", Map::class.java) { it }

    @Bean
    @Singleton
    fun spans(): GraphQLScalarMapping = scalar("Spans", NavigableMap::class.java) { input ->
        val spans: NavigableMap<String, Span> = TreeMap()
        input.forEach { (key, value) ->
            val bounds = value as Map<*, *>
            spans[key] = Span().apply {
                min = (bounds["min"] as Number).toInt()
                max = (bounds["max"] as Number).toInt()
            }
        }
        spans
    }

    @Bean
    @Singleton
    fun weights(): GraphQLScalarMapping = scalar("Weights", SortedMap::class.java) { input ->
        val weights: SortedMap<UUID, Int> = TreeMap()
        input.forEach { (key, value) -> weights[UUID.fromString(key)] = (value as Number).toInt() }
        weights
    }

    @Bean
    @Singleton
    fun tally(): GraphQLScalarMapping = scalar("Tally", ConcurrentMap::class.java) { input ->
        val tally: ConcurrentMap<Region, Int> = ConcurrentHashMap()
        input.forEach { (key, value) -> tally[Region.valueOf(key)] = (value as Number).toInt() }
        tally
    }

    @Bean
    @Singleton
    fun pack(): GraphQLScalarMapping = scalar("Pack", Bag::class.java) { input -> Bag((input["items"] as List<*>).filterNotNull()) }

    @Bean
    @Singleton
    fun tray(): GraphQLScalarMapping = scalar("Tray", Tray::class.java) { input -> Tray((input["items"] as List<*>).filterNotNull()) }

    @Suppress("UNCHECKED_CAST")
    private fun scalar(name: String, type: Class<*>, parse: (Map<String, Any>) -> Any): GraphQLScalarMapping =
        GraphQLScalarMapping.builder(
            GraphQLScalarType.newScalar().name(name).coercing(object : Coercing<Any, Any> {
                override fun serialize(value: Any): Any = value

                override fun parseValue(value: Any): Any = parse(value as Map<String, Any>)

                override fun parseLiteral(value: Any): Any = throw UnsupportedOperationException()
            }).build()
        ).outputClasses(type).inputClasses(type).build()
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

    open fun spans(@Valid spans: List<Span>): String = spans.size.toString()

    open fun spanMap(spans: NavigableMap<String, @Valid Span>): String = spans.size.toString()

    open fun weights(weights: SortedMap<UUID, @Positive Int>): String = weights.size.toString()

    open fun tally(tally: ConcurrentMap<Region, @Positive Int>): String = tally.size.toString()

    open fun pack(pack: Bag<@Positive Int>): String = pack.items.size.toString()

    open fun tray(tray: Tray<@Positive Int>): String = tray.items.size.toString()

    open fun several(@NotBlank name: String, @Positive count: Int, @Valid cart: Cart): String = name
}

// tag::custom-mapper[]
@Singleton
@Requires(property = "tck.scenario", value = "constraint-violation-mapper")
class InvalidInputMapper : GraphQLExceptionMapper<ConstraintViolationException> {
    override fun map(exception: ConstraintViolationException, context: GraphQLExceptionContext): GraphQLError {
        val fields = exception.constraintViolations.map { violation -> violation.propertyPath.last().name }.sorted()
        return context.error("The input is not valid", mapOf("code" to "INVALID_INPUT", "fields" to fields))
    }
}
// end::custom-mapper[]

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "constraint-violation-mapper")
open class RegistrationQueries {

    open fun register(@NotBlank name: String, @Positive seats: Int): String = name
}
