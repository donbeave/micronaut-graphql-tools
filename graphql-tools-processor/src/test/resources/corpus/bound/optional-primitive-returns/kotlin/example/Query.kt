package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.OptionalLong

@Singleton
@GraphQLRootResolver
class Query {

    fun count(): OptionalInt = OptionalInt.of(3)

    fun big(): OptionalLong = OptionalLong.empty()

    fun ratio(): OptionalDouble = OptionalDouble.of(1.5)

    fun counts(): List<OptionalInt> = listOf(OptionalInt.of(1), OptionalInt.empty())

}
