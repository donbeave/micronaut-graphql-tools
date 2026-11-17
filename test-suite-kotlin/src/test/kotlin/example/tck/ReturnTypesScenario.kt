package example.tck

import graphql.GraphqlErrorBuilder
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import java.util.Optional
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
            .error(GraphqlErrorBuilder.newError(environment).message("the second element could not be loaded").build())
            .build()

    fun nulls(): List<Int?> = listOf(1, null, 3)

    fun nothing(): String? = null

    fun scalars(): Scalars = Scalars()
}
