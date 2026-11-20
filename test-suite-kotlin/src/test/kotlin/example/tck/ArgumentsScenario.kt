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

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.input.ArgumentValue
import io.micronaut.graphql.tools.annotation.GraphQLArgument
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `arguments`.

@Introspected
enum class Color { RED, GREEN, BLUE }

@GraphQLInput
class Point {
    var x: Int = 0
    var y: Int = 0
}

@GraphQLInput
class Shape {
    lateinit var name: String
    var color: Color? = null
    lateinit var points: List<Point>
    var note: String? = null
}

// tag::constructor-input[]
@GraphQLInput
data class Stock(
    val sku: String,
    val quantity: Int,
    val weight: Float?,
    val tags: List<String>?,
    val colors: Set<Color>?,
    val comment: ArgumentValue<String>,
)
// end::constructor-input[]

// tag::nested-constructor-input[]
@GraphQLInput
data class Dimensions(val width: Int, val unit: ArgumentValue<String> = ArgumentValue.ofNullable("DEFAULT"))

@GraphQLInput
data class Part(val name: String, val note: ArgumentValue<String> = ArgumentValue.ofNullable("DEFAULT"))

@GraphQLInput
data class Crate(
    val label: String,
    val dimensions: Dimensions,
    val parts: List<Part>,
    val memo: ArgumentValue<String> = ArgumentValue.ofNullable("DEFAULT"),
)
// end::nested-constructor-input[]

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "arguments")
class ArgumentsQueries {

    fun scalars(text: String, number: Int, ratio: Double, flag: Boolean, id: String): String =
        "text=$text number=$number ratio=$ratio flag=$flag id=$id"

    fun defaults(text: String?, count: Int?, color: Color?): String = "text=$text count=$count color=$color"

    fun presence(text: ArgumentValue<String>): String = when {
        text.isOmitted -> "omitted"
        text.isNull -> "null"
        else -> "present:" + text.get()
    }

    fun total(numbers: List<Int>): Int = numbers.sum()

    fun grid(rows: @JvmSuppressWildcards List<List<Int>>): Int = rows.flatten().sum()

    fun stock(stock: Stock): String =
        "sku=${stock.sku} quantity=${stock.quantity} weight=${stock.weight} tags=${stock.tags} colors=${stock.colors} " +
            "comment=${presence(stock.comment)}"

    fun crate(crate: Crate): String =
        "label=${crate.label} dimensions=${crate.dimensions.width} unit=${presence(crate.dimensions.unit)} " +
            "parts=${crate.parts.joinToString(", ", "[", "]") { "${it.name}:${presence(it.note)}" }} memo=${presence(crate.memo)}"

    // tag::by-name[]
    fun window(@GraphQLArgument("in") scope: String, offset: Int, first: Int): String =
        "first=$first offset=$offset in=$scope"
    // end::by-name[]

    fun shape(shape: Shape): String =
        "name=${shape.name} color=${shape.color} points=${shape.points.joinToString(";") { "${it.x},${it.y}" }} note=${shape.note}"
}

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "arguments")
class ArgumentsMutations {

    private val total = AtomicInteger()

    fun add(amount: Int): Int = total.addAndGet(amount)
}

// tag::optional-helpers[]
fun describePresence(text: ArgumentValue<String>): String = text.asOptional().map { "present:$it" }.orElse("absent")

fun rememberIfPresent(text: ArgumentValue<String>, seen: MutableList<String>) = text.ifPresent { seen.add(it) }
// end::optional-helpers[]
