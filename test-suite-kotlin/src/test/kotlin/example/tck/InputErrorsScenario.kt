package example.tck

import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.GraphQLScalarMapping
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import java.util.UUID

// The fixtures of the TCK scenario `input-errors`.

@GraphQLInput
class Basket {
    var lines: List<BasketLine> = emptyList()
}

@GraphQLInput
class BasketLine {
    var ticket: UUID? = null
}

@GraphQLInput
class Guarded {
    var size: Int = 0
        set(@Suppress("UNUSED_PARAMETER") value) = throw IllegalStateException("internal detail")
}

@Factory
@Requires(property = "tck.scenario", value = "input-errors")
class InputErrorsScalars {

    @Bean
    @Singleton
    fun ticket(): GraphQLScalarMapping =
        GraphQLScalarMapping.builder(
            GraphQLScalarType.newScalar().name("Ticket").coercing(object : Coercing<Any, String> {
                override fun serialize(value: Any): String = value.toString()

                override fun parseValue(value: Any): Any = value

                override fun parseLiteral(value: Any): Any = requireNotNull((value as StringValue).value)
            }).build()
        ).outputClasses(UUID::class.java).inputClasses(UUID::class.java).build()

    @Bean
    @Singleton
    fun blob(): GraphQLScalarMapping =
        GraphQLScalarMapping.builder(
            GraphQLScalarType.newScalar().name("Blob").coercing(object : Coercing<Any, Any> {
                override fun serialize(value: Any): Any = value

                override fun parseValue(value: Any): Any = value

                override fun parseLiteral(value: Any): Any = throw UnsupportedOperationException()
            }).build()
        ).outputClasses(Map::class.java).inputClasses(Map::class.java).build()
}

@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "input-errors")
class InputErrorsQueries {

    fun fine(): String = "fine"

    fun ticket(id: UUID): String = id.toString()

    fun ticketNonNull(id: UUID): String = id.toString()

    fun blob(data: Map<String, Any>): String = data.toString()

    fun basket(basket: Basket): String = basket.lines[0].ticket.toString()

    fun guarded(guarded: Guarded): String = "unreachable"
}
