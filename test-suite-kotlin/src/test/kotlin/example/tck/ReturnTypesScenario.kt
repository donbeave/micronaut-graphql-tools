package example.tck

import graphql.ErrorClassification
import graphql.ErrorType
import graphql.GraphQLError
import graphql.language.SourceLocation
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import java.util.Optional
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `return-types`.

@GraphQLType
class Greeting(val message: String)

@GraphQLType
class Scalars {
    val count: Int get() = Int.MAX_VALUE
    val ratio: Double get() = 0.5
    val flag: Boolean get() = true
    val id: String get() = "9007199254740993"
    val big: Double? get() = null
}

@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "return-types")
class ReturnTypesQueries {

    fun plain(): Greeting = Greeting("hello")

    fun present(): Optional<Greeting> = Optional.of(Greeting("present"))

    fun absent(): Optional<Greeting> = Optional.empty()

    fun future(name: String): CompletionStage<Greeting> = CompletableFuture.supplyAsync { Greeting("hello $name") }

    fun futureList(): CompletionStage<List<Greeting>> = CompletableFuture.supplyAsync { listOf(Greeting("one"), Greeting("two")) }

    fun futureAbsent(): CompletionStage<Optional<Greeting>> = CompletableFuture.completedFuture(Optional.empty())

    fun result(): DataFetcherResult<Greeting> = DataFetcherResult.newResult<Greeting>().data(Greeting("from a data fetcher result")).build()

    fun partial(environment: DataFetchingEnvironment): DataFetcherResult<List<Greeting?>> =
        DataFetcherResult.newResult<List<Greeting?>>()
            .data(listOf(Greeting("loaded"), null))
            .error(dataFetchingError(environment, "the second element could not be loaded"))
            .build()

    fun nulls(): List<Int?> = listOf(1, null, 3)

    fun nothing(): String? = null

    fun scalars(): Scalars = Scalars()

    fun primitiveCount(): OptionalInt = OptionalInt.of(7)

    fun primitiveAbsentCount(): OptionalInt = OptionalInt.empty()

    fun primitiveRatio(): OptionalDouble = OptionalDouble.of(0.5)

    fun primitiveCounts(): List<OptionalInt> = listOf(OptionalInt.of(1), OptionalInt.empty(), OptionalInt.of(3))
}

// GraphqlErrorBuilder.newError(environment) returns a recursive generic type that the Kotlin compiler cannot resolve against
// graphql-java 26 (StackOverflowError in compileTestKotlin), so the error that the builder would make is written out.
private fun dataFetchingError(environment: DataFetchingEnvironment, text: String): GraphQLError = object : GraphQLError {
    override fun getMessage(): String = text

    override fun getLocations(): List<SourceLocation>? = listOfNotNull(environment.field.sourceLocation)

    override fun getPath(): List<Any> = environment.executionStepInfo.path.toList()

    override fun getErrorType(): ErrorClassification = ErrorType.DataFetchingException
}
