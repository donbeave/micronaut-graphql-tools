package example.tck

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.ArgumentValue
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `arguments`.

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

    fun shape(shape: Shape): String =
        "name=${shape.name} color=${shape.color} points=${shape.points.joinToString(";") { "${it.x},${it.y}" }} note=${shape.note}"
}

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
